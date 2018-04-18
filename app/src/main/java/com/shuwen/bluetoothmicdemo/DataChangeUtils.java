package com.shuwen.bluetoothmicdemo;


public class DataChangeUtils {

    /**
     * 转换short数组为byte数组，转换原则是将最低位转换优先。
     *
     * @param source 源数组，short类型
     * @return
     */
    public static byte[] changeShortsToBytes(short[] source) {

        byte[] b = new byte[source.length*2];
        for (int i=0; i<source.length; i++) {
            int temp = source[i];

            // 将最低位保存在最低位
            b[i*2] = (byte) (temp & 0xff);
            // 向右移8位
            b[i*2+1] = (byte) (temp >> 8);
        }

        return b;
    }

    /**
     * 转换byte数组为short数组，转换原则是将最低位转换优先。
     *
     * @param source 源数组byte类型
     * @return
     */
    public static short[] changeBytesToShorts(byte[] source) {
        int count = source.length >> 1;
        short[] dest = new short[count];
        for (int i = 0; i < count; i++) {
            dest[i] = (short) (source[i*2 + 1] << 8 | source[2*i] & 0xff);
        }
        return dest;

    }

    /**
     * OPUS格式数据转换为OPU格式，转换方式是在字节数组前增加一个字节，表示后面的具体长度。
     *
     * @param opusData opus数据
     * @return opu数据
     */
    public static byte[] opusToOpu(byte[] opusData, int len) {
        byte[] result = new byte[len + 1];
        result[0] = (byte) len;
        System.arraycopy(opusData, 0, result, 1, len);
        return result;
    }

}
