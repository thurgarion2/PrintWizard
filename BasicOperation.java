public class BasicOperation {

    public static void main(String[] args) {
        int a = 0;
        int b = 1;
        int c = 4;
        a = 10;
        int x = a + b + c;

        try{
            int i = 0;
            throw new IllegalStateException("illegal");
        }catch (IllegalArgumentException state){
            int k = 10;
            System.out.println("hello");
        }

    }

}
