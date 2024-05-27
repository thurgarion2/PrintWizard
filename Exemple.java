import java.lang.reflect.Field;

public class Exemple {
    public static void main(String[] args) {
        Object x = null;
        int i = 0;
        while(i<5){
            for(int j=0; j<5; ++j){
                if(i==j){
                    System.out.println(i);
                }else if(i==j+1){
                    System.out.println(j);
                }
                foo(i*10, j);
            }
            ++i;
        }

    }
    public static int foo(int a, int b) {

        return a+b;
    }
}
