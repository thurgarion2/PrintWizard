import java.lang.reflect.Array;
import java.util.ArrayList;

public class BasicOperation {

    public static void main(String[] args) {
        ArrayList<Integer> arr = new ArrayList<>();
        Class<?> clazz = arr.getClass();
        System.out.println(clazz.isArray());


    }

    static class A {
        void f(){}
    }

    static class Foo{
        static void f(){}
    }

    static void foo(Object o){
        Class<?> clazz = o.getClass();
        if(clazz.isArray()){
           System.out.println(System.identityHashCode(o));
           System.out.println(Array.getLength(o));
           System.out.println(Array.get(o,0));
           System.out.println(Array.get(Array.get(o,0),0));
        }
    }

    static void bar(Object o){
        System.out.println(o);
    }

}
