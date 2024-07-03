import java.lang.reflect.Array;
import java.util.ArrayList;

public class BasicOperation {

    public static void main(String[] args) {
        A a = new A();
        a.foo(A.number1()).bar(A.number2());

    }

    public static class A {
        static int number1(){
            return 1;
        }

        static int number2(){
            return 1;
        }
        A foo(int x){
            return this;
        }

        A bar(int x){
            return this;
        }
    }

}
