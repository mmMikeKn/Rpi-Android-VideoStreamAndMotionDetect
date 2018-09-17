#!/usr/bin/env python
import socket
import threading
import time, calendar
from datetime import datetime
import sys, subprocess, struct
import logging
import argparse
import binascii

# https://picamera.readthedocs.io/en/release-1.13/install.html
import picamera
import picamera.array
import RPi.GPIO as GPIO
import spidev
# sudo apt-get update sudo apt-get install python-dev
# git clone git://github.com/doceme/py-spidev
# cd py-spidev
# sudo python setup.py install
import numpy as np

stepMotorHorizontalGPIO = [17, 27, 22, 23]
stepMotorVerticalGPIO = [24, 25, 8, 7]
password = 'qscfew'
fileNameFormat = '%y-%m-%d_%H-%M-%S.%f'
snapshotExec = '/home/pi/motion_cp.sh'


# -------------------------------------------------------------------------------------------------------------------
class StepMotor:
    _stepTable = [1, 3, 2, 6, 4, 12, 8, 9]
    _stepDelay = 0.005  # f < 300Hz
    _maxRotate = 45  # degree

    def __init__(self, wires):
        self._thread = None
        self._thread_interrupt = True
        [self._gpio_orange, self._gpio_yellow, self._gpio_pink, self._gpio_blue] = wires
        self._rotorPos = 0
        GPIO.setup(self._gpio_orange, GPIO.OUT)
        GPIO.setup(self._gpio_yellow, GPIO.OUT)
        GPIO.setup(self._gpio_pink, GPIO.OUT)
        GPIO.setup(self._gpio_blue, GPIO.OUT)
        self.power_off()

    def _steps(self, k):
        # k = -1...+1
        # 28YBJ-48 2048 steps/rotation - 360
        # +-0.99 = 45 degree.
        # logger.debug('thread  _steps. k:'+str(k))
        ds = int(2048 * StepMotor._maxRotate / 360.0 * k)
        logger.debug('thread _steps. ds:' + str(ds) + ' orange:' + str(self._gpio_orange) + ' yellow:' + str(
            self._gpio_yellow) + ' pink:' + str(self._gpio_pink) + ' blue:' + str(self._gpio_blue))

        while abs(ds) > 1 and not self._thread_interrupt:
            time.sleep(self._stepDelay)
            if self._rotorPos >= len(StepMotor._stepTable):
                self._rotorPos = 0
            if self._rotorPos < 0:
                self._rotorPos = len(StepMotor._stepTable) - 1
            flags = StepMotor._stepTable[self._rotorPos]
            GPIO.output(self._gpio_orange, (flags & 1) != 0)
            GPIO.output(self._gpio_yellow, (flags & 2) != 0)
            GPIO.output(self._gpio_pink, (flags & 4) != 0)
            GPIO.output(self._gpio_blue, (flags & 8) != 0)
            if ds < 0:
                ds += 1
                self._rotorPos -= 1
            else:
                ds -= 1
                self._rotorPos += 1
        logger.debug(
            'power off orange:' + str(self._gpio_orange) + ' yellow:' + str(self._gpio_yellow) + ' pink:' + str(
                self._gpio_pink) + ' blue:' + str(self._gpio_blue))
        self.power_off()

    def power_off(self):
        GPIO.output(self._gpio_orange, False)
        GPIO.output(self._gpio_yellow, False)
        GPIO.output(self._gpio_pink, False)
        GPIO.output(self._gpio_blue, False)

    def stop(self):
        self._thread_interrupt = True
        if self._thread is not None:
            logger.debug('start interrupting the STEP MOTOR thread..')
            self._thread.join()
            logger.debug('finish interrupting the STEP MOTOR thread')

    def do(self, k):
        self.stop()
        self._thread_interrupt = False
        self._thread = threading.Thread(name='step motor', target=self._steps, args=[k])
        self._thread.start()


# -------------------------------------------------------------------------------------------------------------------
class DetectMotion(picamera.array.PiMotionAnalysis):
    cfg_motionThresholdValue = 70 * 70
    cfg_motionThresholdCnt = 110
    cfg_motionThresholdSAD = 100
    cfg_motionNoiseFilter = 'Y'
    cfg_motionDetectTimeWindow = 1.5
    cfg_motionNumberInTimeWindow = 3
    cfg_motionSnapshotDt = 1.0

    motionValue = 0
    motionData = None

    def __init__(self, c, _snapshot_event):
        self._snapshotEvent = _snapshot_event
        self._motion_start = datetime.now()
        self._motion_cnt = 0
        super(type(self), self).__init__(c)

    def analyse(self, a):
        DetectMotion.motionRaw = np.copy(a)
        b = (
            np.square(a['x'].astype(np.uint16)) +
            np.square(a['y'].astype(np.uint16))
        ).astype(np.uint16)
        b = b > DetectMotion.cfg_motionThresholdValue
        b = np.logical_and(b, (a['sad'] < DetectMotion.cfg_motionThresholdSAD))
        if DetectMotion.cfg_motionNoiseFilter == 'Y':
            b = np.logical_and(b, np.logical_and((a['x'] != 0), (a['y'] != 0)))
        n = b.sum()
        if n > DetectMotion.cfg_motionThresholdCnt:
            logger.debug('motion !!! ' + str(self._motion_cnt))
            t = datetime.now()
            if (t - self._motion_start).total_seconds() > DetectMotion.cfg_motionDetectTimeWindow:
                logger.debug('motion init for:' + str((t - self._motion_start).total_seconds()))
                self._motion_start = t
                self._motion_cnt = 0
            elif self._motion_cnt >= DetectMotion.cfg_motionNumberInTimeWindow:
                self._motion_cnt = 0
                DetectMotion.motionValue = n
                DetectMotion.motionData = np.copy(a)
                self._snapshotEvent.set()
            else:
                logger.debug('motion cnt++')
                self._motion_cnt += 1


class VideoProcessing:
    motionTime = 0
    snapshotBodyJpg = None
    snapshotMotionValue = 0
    motionData = None
    lockSnapshot = threading.Lock()

    def __init__(self):
        self._thread = None
        self._threadSnapshot = None
        self._stream_interrupt = threading.Event()
        self._stream_interrupt.set()
        self._snapshotEvent = threading.Event()
        self._snapshotEvent.clear()

        # default values
        self.stream_width = '1920'
        self.stream_height = '1080'
        self.stream_fps = '15'
        self.stream_bitrate = '100000'
        self.stream_iso = '0'
        # 10..40, 0 is "reasonable quality", 10 is extremely hight quality, 40 is extremely low
        self.stream_quality = '25'

    def _snapshot(self):
        logger.debug('start snapshot thread')
        while True:
            self._snapshotEvent.wait()
            if self._stream_interrupt.isSet():
                break
            file_name = '/tmp/' + datetime.now().strftime(fileNameFormat) + '.jpg'
            logger.debug('start. capture snapshot:' + file_name)
            camera.capture(file_name, use_video_port=True, format='jpeg', quality=80)
            with VideoProcessing.lockSnapshot:
                with open(file_name, mode='rb') as file:
                    VideoProcessing.snapshotBodyJpg = file.read()
                VideoProcessing.motionTime = calendar.timegm(time.gmtime())
                VideoProcessing.snapshotMotionValue = DetectMotion.motionValue
                VideoProcessing.motionData = np.copy(DetectMotion.motionData)
            logger.debug('start. send snapshot. mv number:' + str(VideoProcessing.snapshotMotionValue))
            if snapshotExec is not None:
                p = subprocess.Popen('exec ' + snapshotExec + ' ' + file_name, stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE, shell=True)
                while True:
                    s = p.stdout.readline()
                    if not s:
                        break
                    logging.debug("send snapshot stdout << " + s)
                while True:
                    s = p.stderr.readline()
                    if not s:
                        break
                    logging.debug("send snapshot stderr << " + s)
                p.wait()
            logger.debug('end. snapshot')
            time.sleep(DetectMotion.cfg_motionSnapshotDt)
            if self._stream_interrupt.isSet():
                break
            self._snapshotEvent.clear()
        logger.debug('end snapshot thread')

    def _stream_thread(self, out_stream):
        camera.resolution = (int(self.stream_width), int(self.stream_height))
        camera.framerate = int(self.stream_fps)
        camera.iso = int(self.stream_iso)
        try:
            logger.debug('stream camera.start_preview')
            camera.start_preview()
            time.sleep(1)
            camera.start_recording(out_stream, format='h264', quality=int(self.stream_quality),
                                   bitrate=int(self.stream_bitrate),
                                   inline_headers=True)
            logger.debug('stream wait interrupting')
            self._stream_interrupt.wait()
        except socket.error:
            logger.debug('stream thread: socket.error')
            pass
        finally:
            camera.stop_recording()
            logger.debug('stream camera.stop_recording()')

    def _detect_motion_thread(self):
        try:
            camera.resolution = (1296, 730)
            camera.framerate = 10
            logger.debug('motion camera.start_recording')
            stream = picamera.PiCameraCircularIO(camera, seconds=10, bitrate=1000000)
            camera.start_recording(stream, format='h264', motion_output=DetectMotion(camera, self._snapshotEvent))
            self._stream_interrupt.wait()
        finally:
            camera.stop_recording()
            logger.debug('motion camera.stop_recording()')

    def stop(self):
        logger.debug('start interrupting VideoProcessing thread')
        self._stream_interrupt.set()
        if self._thread is not None:
            logger.debug(' start interrupting the camera thread.')
            self._thread.join()
            logger.debug(' end interrupting the camera thread')
            self._thread = None
        self._snapshotEvent.set()
        if self._threadSnapshot is not None:
            logger.debug(' start interrupting the snapshot thread.')
            self._threadSnapshot.join()
            logger.debug(' end interrupting the snapshot thread')
            self._threadSnapshot = None
        self._stream_interrupt.clear()
        self._snapshotEvent.clear()
        logger.debug('end interrupting VideoProcessing thread')

    def start_motion_detector(self):
        self.stop()
        self._thread = threading.Thread(name='motion detector', target=self._detect_motion_thread, args=[])
        self._thread.start()
        self._snapshotEvent.clear()
        self._threadSnapshot = threading.Thread(name='snapshot', target=self._snapshot, args=[])
        self._threadSnapshot.start()

    def start(self, a, out_stream):
        [self.stream_width, self.stream_height, self.stream_fps, self.stream_bitrate, self.stream_quality,
         self.stream_iso] = a
        logger.debug('new stream w:%s h:%s fps:%s br:%s\n' % (
            self.stream_width, self.stream_height, self.stream_fps, self.stream_bitrate))
        self.stop()
        self._thread = threading.Thread(name='tcp stream', target=self._stream_thread, args=[(out_stream)])
        self._thread.start()


# ====================================================================================================================

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='video streamer and motion detector')
    parser.add_argument("-d", "--debug_level", dest="logLevel",
                        choices=['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'], help='Set the logging level',
                        default='DEBUG')
    parser.add_argument("-f", "--log_file", dest="logFile", required=False, help='log file')
    args = parser.parse_args()

    if args.logFile:
        hdlr = logging.FileHandler(args.logFile, 'a')
    else:
        hdlr = logging.StreamHandler()
    hdlr.setFormatter(logging.Formatter(u'[LINE:%(lineno)d]# %(levelname)-8s [%(asctime)s]  %(message)s'))
    logging.getLogger().addHandler(hdlr)
    logging.getLogger().setLevel(getattr(logging, args.logLevel))
    logger = logging.getLogger('vh')
    logger.info('----------- start vhost -----------')
    GPIO.setmode(GPIO.BCM)
    GPIO.setwarnings(False)

    spi = spidev.SpiDev()
    spi.open(0, 0)  # /dev/spidev0.0
#    logger.debug('default spi mode:' + str(spi.mode) + ' speed:' + str(spi.max_speed_hz) + ' bits_per_word:' + str(
#        spi.bits_per_word) + ' loop:' + str(spi.loop) + ' 3wire:' + str(spi.threewire) + ' lsbfirst:' + str(
#        spi.lsbfirst) + ' no cs:' + str(spi.no_cs))
    spi.max_speed_hz = 100000
    spi.threewire = False  # SPI_InitStructure.SPI_Direction = SPI_Direction_2Lines_FullDuplex;
    spi.loop = False
    spi.bits_per_word = 8  # SPI_InitStructure.SPI_DataSize = SPI_DataSize_8b;
    spi.lsbfirst = False  # SPI_InitStructure.SPI_FirstBit = SPI_FirstBit_MSB;
    spi.no_cs = True  # SPI_InitStructure.SPI_NSS = SPI_NSS_Soft
    spi.mode = 0
    # SPI_IOC_WR_MODE: SPI_CPHA=0x01(trailing edge=set),
    # SPI_CPOL=0x02(idle high=set):
    #   SPI_InitStructure.SPI_CPOL = SPI_CPOL_Low
    #   SPI_InitStructure.SPI_CPHA = SPI_CPHA_1EdgeCPOL
    logger.debug('stm32f103 spi mode:' + str(spi.mode) + ' speed:' + str(spi.max_speed_hz) + ' bits_per_word:' + str(
        spi.bits_per_word) + ' loop:' + str(spi.loop) + ' 3wire:' + str(spi.threewire) + ' lsbfirst:' + str(
        spi.lsbfirst) + ' no cs:' + str(spi.no_cs))

    camera = picamera.PiCamera()
    server_socket = socket.socket()
    vs = VideoProcessing()
    motorH = StepMotor(stepMotorHorizontalGPIO)
    motorV = StepMotor(stepMotorVerticalGPIO)
    try:
        spi = spidev.SpiDev()
        spi.open(0, 0)  # spi.open(bus, device) -> /dev/spidev<bus>.<device>
        # spi.mode = 0b01
        # SPI mode as two bit pattern of clock polarity and phase [CPOL|CPHA], min: 0b00 = 0, max: 0b11 = 3

        server_socket.bind(('0.0.0.0', 8081))
        server_socket.listen(0)
        restartMotionDetector = True
        while True:
            try:
                if restartMotionDetector:
                    vs.start_motion_detector()
                restartMotionDetector = True
                conn, address = server_socket.accept()
                logging.debug('accept:' + str(address))
                inp = conn.makefile("rt")
                isAuthenticated = False
                while True:
                    data = inp.readline().strip()
                    if not data:
                        logger.debug('no commands')
                        break
                    l = data.split(',')
                    args = l[1:]
                    cmd = l[0]
                    logger.debug('rcv cmd: "%s" args:%s' % (cmd, str(args)))
                    if cmd == 'AUTH' and args[0] == password:
                        isAuthenticated = True
                        continue
                    if not isAuthenticated:
                        logger.error('request does not authenticated')
                        break
                    if cmd == 'START_STREAM':
                        vs.start(args, conn.makefile("wb"))
                    elif cmd == 'ROTATE':
                        v = float(args[1])
                        if args[0] == 'H':
                            motorH.do(v)
                        else:
                            motorV.do(v)
                    elif cmd == 'REBOOT':
                        logger.info('=============== stop ================')
                        sys.exit(0)
                    elif cmd == 'SPI':
                        v = spi.xfer2(bytearray.fromhex(args[1]))
                        if args[0] == 'r':
                            res = struct.pack("{}B".format(len(v)), *v)
                            conn.send(res)
                            logger.debug('SPI >> ' + binascii.hexlify(res))
                    elif cmd == 'FINISH':
                        v = int(args[0])
                        DetectMotion.cfg_motionThresholdValue = v * v
                        DetectMotion.cfg_motionThresholdCnt = int(args[1])
                        DetectMotion.cfg_motionThresholdSAD = int(args[2])
                        DetectMotion.cfg_motionNoiseFilter = args[3]
                        DetectMotion.cfg_motionSnapshotDt = float(args[4])
                        DetectMotion.cfg_motionDetectTimeWindow = float(args[5])
                        DetectMotion.cfg_motionNumberInTimeWindow = int(args[6])
                        logger.debug(
                            'set motion threshold value:' + str(DetectMotion.cfg_motionThresholdValue) +
                            ' cnt:' + str(DetectMotion.cfg_motionThresholdCnt) +
                            ' sad:' + str(DetectMotion.cfg_motionThresholdSAD) +
                            ' noise filter:' + str(DetectMotion.cfg_motionNoiseFilter) +
                            ' snapshot dt:' + str(DetectMotion.cfg_motionSnapshotDt) +
                            ' motion time window:' + str(DetectMotion.cfg_motionDetectTimeWindow) +
                            ' motion time window number:' + str(DetectMotion.cfg_motionNumberInTimeWindow)
                        )
                        vs.stop()
                        break
                    elif cmd == 'MOTION_INFO':
                        restartMotionDetector = False
                        with VideoProcessing.lockSnapshot:
                            logger.debug('MOTION_INFO time:' + str(VideoProcessing.motionTime))
                            conn.send(struct.pack('!HLH', 0x37F4, VideoProcessing.motionTime,
                                                  VideoProcessing.snapshotMotionValue))
                            if VideoProcessing.snapshotBodyJpg is not None and (args[0] == 'S' or args[0] == 'F'):
                                conn.send(struct.pack('!I', len(VideoProcessing.snapshotBodyJpg)))
                                conn.send(VideoProcessing.snapshotBodyJpg)
                            else:
                                conn.send(struct.pack('!I', 0))
                            if VideoProcessing.motionData is not None and args[0] == 'F':
                                conn.send(struct.pack('!HH', VideoProcessing.motionData.shape[0],
                                                      VideoProcessing.motionData.shape[1]))
                                for i in VideoProcessing.motionData:
                                    for j in i:
                                        conn.send(struct.pack('!bbh'.format(len(j)), *j))
                            else:
                                conn.send(struct.pack('!HH', 0, 0))
                            VideoProcessing.motionTime = 0
                            VideoProcessing.snapshotBodyJpg = None
                        break
                    else:
                        logger.error('undefined data:"' + data + '"')
            except socket.error as e:
                logger.debug(e, exc_info=True)
            finally:
                conn.close()
    finally:
        logger.info('---------- exit ----------')
        vs.stop()
        motorH.stop()
        motorV.stop()
        server_socket.close()
        spi.close()
        camera.close()
