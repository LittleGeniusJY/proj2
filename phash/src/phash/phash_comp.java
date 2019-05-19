package phash;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Scanner;

import javax.imageio.ImageIO;

/*
* function: 用汉明距离进行图片相似度检测的Java实现
* pHash-like image hash.
* Author: Sun Huaqiang
* Based On: http://www.hackerfactor.com/blog/index.php?/archives/432-Looks-Like-It.html
*/
public class phash_comp {
    static String[] image_name = new String[50000];

    private int size = 32;
    private int smallerSize = 8;

    public phash_comp() {
        this.initCoefficients();
    }

    private phash_comp(int size, int smallerSize) {
        this.size = size;
        this.smallerSize = smallerSize;

        this.initCoefficients();
    }

    private int distance(String s1, String s2) {
        int counter = 0;
        for (int k = 0; k < s1.length(); k++) {
            if (s1.charAt(k) != s2.charAt(k)) {
                counter++;
            }
        }
        return counter;
    }

    // Returns a 'binary string' (like. 001010111011100010) which is easy to do a hamming distance on.
    private String getHash(InputStream is) throws Exception {
        BufferedImage img = ImageIO.read(is);

        /*
         * 1. Reduce size(缩小尺寸). Like Average Hash, pHash starts with a small
         * image. However, the image is larger than 8x8; 32x32 is a good
         * size.This is really done to simplify the DCT computation and not
         * because it is needed to reduce the high frequencies.
         */
        img = this.resize(img, this.size, this.size);

        /*
         * 2. Reduce color(简化色彩). The image is reduced to a grayscale just to
         * further simplify the number of computations.
         */
        img = this.grayscale(img);

        double[][] vals = new double[this.size][this.size];

        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                vals[x][y] = getBlue(img, x, y);
            }
        }

        /*
         * 3. Compute the DCT(计算DCT). The DCT(Discrete Cosine Transform,离散余弦转换)
         * separates the image into a collection of frequencies and scalars.
         * While JPEG uses an 8x8 DCT, this algorithm uses a 32x32 DCT.
         */
        long start = System.currentTimeMillis();
        double[][] dctVals = this.applyDCT(vals);
        //        System.out.println("DCT_COST_TIME: " + (System.currentTimeMillis() - start));

        /*
         * 4. Reduce the DCT. This is the magic step. While the DCT is 32x32,
         * just keep the top-left 8x8. Those represent the lowest frequencies in
         * the picture.
         */
        /*
         * 5. Compute the average value. Like the Average Hash, compute the mean
         * DCT value (using only the 8x8 DCT low-frequency values and excluding
         * the first term since the DC coefficient can be significantly
         * different from the other values and will throw off the average).
         */
        double total = 0;

        for (int x = 0; x < this.smallerSize; x++) {
            for (int y = 0; y < this.smallerSize; y++) {
                total += dctVals[x][y];
            }
        }
        total -= dctVals[0][0];

        double avg = total / ((this.smallerSize * this.smallerSize) - 1);

        /*
         * 6. Further reduce the DCT. This is the magic step. Set the 64 hash
         * bits to 0 or 1 depending on whether each of the 64 DCT values is
         * above or below the average value. The result doesn't tell us the
         * actual low frequencies; it just tells us the very-rough relative
         * scale of the frequencies to the mean. The result will not vary as
         * long as the overall structure of the image remains the same; this can
         * survive gamma and color histogram adjustments without a problem.
         */
        String hash = "";

        for (int x = 0; x < this.smallerSize; x++) {
            for (int y = 0; y < this.smallerSize; y++) {
                if (x != 0 && y != 0) {
                    hash += (dctVals[x][y] > avg ? "1" : "0");
                }
            }
        }

        return hash;
    }

    private BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return resizedImage;
    }

    private ColorConvertOp colorConvert = new ColorConvertOp(
            ColorSpace.getInstance(ColorSpace.CS_GRAY), null);

    private BufferedImage grayscale(BufferedImage img) {
        this.colorConvert.filter(img, img);
        return img;
    }

    private static int getBlue(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y)) & 0xff;
    }

    // DCT function stolen from http://stackoverflow.com/questions/4240490/problems-with-dct-and-idct-algorithm-in-java

    private double[] c;

    private void initCoefficients() {
        this.c = new double[this.size];

        for (int i = 1; i < this.size; i++) {
            this.c[i] = 1;
        }
        this.c[0] = 1 / Math.sqrt(2.0);
    }

    private double[][] applyDCT(double[][] f) {
        int N = this.size;

        double[][] F = new double[N][N];
        for (int u = 0; u < N; u++) {
            for (int v = 0; v < N; v++) {
                double sum = 0.0;
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        sum += Math.cos(((2 * i + 1) / (2.0 * N)) * u * Math.PI)
                                * Math.cos(
                                        ((2 * j + 1) / (2.0 * N)) * v * Math.PI)
                                * (f[i][j]);
                    }
                }
                sum *= ((this.c[u] * this.c[v]) / 4.0);
                F[u][v] = sum;
            }
        }
        return F;
    }

    /**
     *
     * @param img1
     * @param img2
     * @param tv
     * @return boolean
     */
    public boolean imgChk(String img1, String img2, int tv) {
        phash_comp p = new phash_comp();
        String image1;
        String image2;

        try {
            image1 = p.getHash(new FileInputStream(new File(img1)));
            image2 = p.getHash(new FileInputStream(new File(img2)));
            int dt = p.distance(image1, image2);
            System.out
                    .println("[" + img1 + "] : [" + img2 + "] Score is " + dt);
            if (dt <= tv) {
                return true;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    //the mergesort is referenced from "https://www.cnblogs.com/of-fanruice/p/7678801.html"
    public static int[] sort(int[] a, String[] str, int low, int high) {
        int mid = (low + high) / 2;
        if (low < high) {
            sort(a, str, low, mid);
            sort(a, str, mid + 1, high);
            //左右归并
            merge(a, str, low, mid, high);
        }
        return a;
    }

    public static void merge(int[] a, String[] str, int low, int mid,
            int high) {
        String[] temp_str = new String[high - low + 1];
        int[] temp = new int[high - low + 1];
        int i = low;
        int j = mid + 1;
        int k = 0;
        // 把较小的数先移到新数组中
        while (i <= mid && j <= high) {
            if (a[i] < a[j]) {
                temp[k] = a[i];
                temp_str[k] = str[i];
                k++;
                i++;
            } else {
                temp[k] = a[j];
                temp_str[k] = str[j];
                k++;
                j++;
            }
        }
        // 把左边剩余的数移入数组
        while (i <= mid) {
            temp[k] = a[i];
            temp_str[k] = str[i];
            k++;
            i++;
        }
        // 把右边边剩余的数移入数组
        while (j <= high) {
            temp[k] = a[j];
            temp_str[k] = str[j];
            k++;
            j++;
        }
        // 把新数组中的数覆盖nums数组
        for (int x = 0; x < temp.length; x++) {
            a[x + low] = temp[x];
            str[x + low] = temp_str[x];
        }
    }

    public static void main(String[] args) {
        Scanner sc1 = new Scanner(System.in);
        String path_base = "";
        System.out.println("please enter the path of the image file:");
        path_base = sc1.nextLine();
        // = "D:/t7.jpg";

        phash_comp hashc = new phash_comp();
        String hashcode_base = "";

        try {
            hashcode_base = hashc
                    .getHash(new FileInputStream(new File(path_base)));
        } catch (FileNotFoundException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        FileInputStream inputStream = null;
        Scanner sc = null;
        String fileName = "";

        String txt_fileName = "";
        ///////////////////遍历埃菲尔铁塔文件夹的图片
        File file = new File("/hashtxt");

        File[] fileList = file.listFiles();

        int count = 0;
        int[] dis_arr = new int[10000];
        int[] after_sort = new int[10000];

        for (int k = 0; k < fileList.length; k++) {
            if (fileList[k].isFile()) {
                fileName = fileList[k].getName();
                try {
                    inputStream = new FileInputStream("/hashtxt/" + fileName);
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                sc = new Scanner(inputStream, "UTF-8");
                while (sc.hasNextLine()) {

                    String line = sc.nextLine();
                    String[] s = line.split(" ");
                    //你可以把得到的值强转成int
                    String img_name = s[0];
                    String hashcode = s[1];

                    int dt = hashc.distance(hashcode_base, hashcode);

                    System.out.println("文件：" + img_name + " 他的hashcode是："
                            + hashcode + "   差距是" + dt);
                    dis_arr[count] = dt;
                    image_name[count] = img_name;

                    count = count + 1;

                    //System.out.println("");
                    //feature_num++; ///每张图片对应多少特征点
                }
            }
        }

        //   for (int a = 0; a < count; a++) {
        //      System.out.println(dis_arr[a]);
        //  }
        //    System.out.println(count);

        after_sort = sort(dis_arr, image_name, 0, 6379);

        //  for (int a = 0; a < count; a++) {
        //     System.out.println(dis_arr[a]);
        // }
        System.out.println("");
        System.out.println("the top 12 most similar picture should be:");
        for (int a = 0; a < 12; a++) {
            System.out.println(image_name[a] + "  " + after_sort[a]);
        }

        /*
         * try { File file1_txt = new File("D:/hashtxt/triomphe.txt"); ///create
         * the txt of eiffel FileWriter fw;
         *
         * fw = new FileWriter(file1_txt); BufferedWriter bw = new
         * BufferedWriter(fw);///////////////////////
         *
         * File file = new File("D:/CSE534/新建文件夹 (3)/paris/triomphe");
         *
         * File[] fileList = file.listFiles();
         *
         * for (int i = 0; i < fileList.length; i++) { if (fileList[i].isFile())
         * { String fileName = fileList[i].getName();
         *
         * String path = "D:/CSE534/新建文件夹 (3)/paris/triomphe/" + fileName; //
         * phash_comp hashc = new phash_comp(); String hashcode = "";
         *
         * try { hashcode = hashc .getHash(new FileInputStream(new File(path)));
         * bw.write(fileName + " "); bw.write(hashcode); bw.flush();
         *
         * } catch (Exception e) { // TODO Auto-generated catch block
         * e.printStackTrace(); }
         *
         * int dt = hashc.distance(hashcode_base, hashcode);
         *
         * System.out.println("文件：" + fileName); System.out
         * .println("他的hashcode是：" + hashcode + "   差距是" + dt);
         *
         * }
         *
         * bw.write("\r\n"); bw.flush(); }
         *
         * } catch (IOException e1) { // TODO Auto-generated catch block
         * e1.printStackTrace(); } ////////////////////////////
         */
    }
}
