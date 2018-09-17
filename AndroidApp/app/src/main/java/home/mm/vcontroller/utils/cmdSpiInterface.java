package home.mm.vcontroller.utils;

public interface cmdSpiInterface {
    boolean withTorch();
    public void sendCmdSPI(byte cmd[]);
}
