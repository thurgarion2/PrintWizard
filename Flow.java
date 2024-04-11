public class Flow {

    public static void main(String[] args) {
        bar(0);

        for(int i=0; i<10;++i){
            if(i%2==0){
                continue;
            }
            if (i==7){
                break;
            }
            System.out.println(i);
        }

    }

    public static void foo(int x){
        if(x==0){
            throw new RuntimeException();
        }
        foo(x-1);
    }

    public static void bar(int x){
        if(x==0) {
            try {
                foo(4);
            } catch (RuntimeException e) {
                System.out.println("exception");
            } finally {
                int y = 0;
            }
        }
    }
}
