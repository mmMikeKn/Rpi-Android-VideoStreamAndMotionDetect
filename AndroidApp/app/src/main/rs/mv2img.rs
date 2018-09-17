#pragma version(1)
#pragma rs java_package_name(home.mm.vcontroller)
#pragma rs_fp_relaxed

rs_allocation gInputImg;
static int imgX, imgY, mvSizeX, mvSizeY;
static int tresholdValue, tresholdSAD;
static bool noiseFilter;

void setupInputImg(rs_allocation img, int tValue, int tSAD, bool nf, int mvX, int mvY) {
 gInputImg = img;
 imgY = rsAllocationGetDimY(gInputImg);
 imgX = rsAllocationGetDimX(gInputImg);
 tresholdValue = tValue*tValue;
 tresholdSAD = tSAD;
 noiseFilter = nf;
 mvSizeX = mvX;
 mvSizeY = mvY;
// cnt2test =0;
 //rsDebug("(imgX,imgY, mvSizeX, mvSizeY)",imgX,imgY,mvSizeX, mvSizeY);
}

//-------------------------------------

static void drawLine(int x1, int y1, int x2, int y2, uchar4 rgb) {
  int deltaX = abs(x2 - x1), deltaY = abs(y2 - y1);
  int signX = x1 < x2 ? 1 : -1;
  int signY = y1 < y2 ? 1 : -1;
  int error = deltaX - deltaY;
  //if(x >= 0 && x < imgX &&  y >= 0 && y < imgY) rsSetElementAt_uchar4(gColorMapImage, rgb, x2, y2);
  while(x1 != x2 || y1 != y2) {
    if(x1 >= 0 && x1 < imgX && y1 >= 0 && y1 < imgY) rsSetElementAt_uchar4(gInputImg, rgb, x1, y1);
    int error2 = error * 2;
    if(error2 > -deltaY) { error -= deltaY; x1 += signX; }
    else { error += deltaX; y1 += signY; }
  }
}

const static uchar4 rgbRed = {255,0,0,255};
const static uchar4 rgbGreen = {0, 255,0,255};

/*
int cnt2test;
int __attribute__((kernel)) getCnt(int x){
    return cnt2test;
}
*/

int32_t __attribute__((kernel)) addMv2Image(int32_t in, uint32_t x) {
  int8_t vx = (int8_t)(in >> 24);
  int8_t vy = (int8_t)(in >> 16);
  uint16_t sad = (uint16_t)in; //sum of absolute differences (SAD)

  int mvX = x % mvSizeX;
  int mvY = x / mvSizeX;
  int xx = mvX*16+8, yy = mvY*16+8; // MV block size 16x16
  int vsize = vx*vx+vy*vy;
  if(vsize > 2) {
    if(!noiseFilter || (vx != 0 && vy != 0)) {
     //sad = sad >> 1;
      uchar4 rgb = (vsize > tresholdValue && sad < tresholdSAD) ? rgbRed:rgbGreen;
      if(sad > 255) sad = 255;
      else rgb.b = sad;
      drawLine(xx, yy, xx+vx, yy+vy, rgb);
    }
//    if(vsize > treshold) rsAtomicInc(&cnt2test);
  }
  return in;
}

int32_t __attribute__((kernel)) addRectangles2Image(int32_t in, uint32_t x) {
  int8_t vx = (int8_t)(in >> 24);
  int8_t vy = (int8_t)(in >> 16);
  uint16_t sad = (uint16_t)in; //sum of absolute differences (SAD)

  int mvX = x % mvSizeX;
  int mvY = x / mvSizeX;
  int xx = mvX*16, yy = mvY*16; // MV block size 16x16
  int vsize = vx*vx+vy*vy;
  if(vsize > 2) {
    if(!noiseFilter || (vx != 0 && vy != 0)) {
      uchar4 rgb = (vsize > tresholdValue && sad < tresholdSAD) ? rgbRed:rgbGreen;
      drawLine(xx, yy, xx+16, yy, rgb);
      drawLine(xx+16, yy, xx+16, yy+16, rgb);
      drawLine(xx+16, yy+16, xx, yy+16, rgb);
      drawLine(xx, yy+16, xx, yy, rgb);
     }
  }
  return in;
}


const static float3 gMonoMult = {0.299f/2, 0.587f/2, 0.114f/2};
uchar4 __attribute__((kernel)) copyImage2Mono(uchar4 in, uint32_t x, uint32_t y) {
  float4 f4 = rsUnpackColor8888(in);
  float3 mono = dot(f4.rgb, gMonoMult);
  uchar4 out = rsPackColorTo8888(mono);
  return out;
}
