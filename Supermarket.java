public class Supermarket {

    public static void main(String[] args) {
        int x = 0, y = 1, z = 3;
        int sum = x + y + z;

        Article cheese = new Article("cheese", new DollarAmount(5));
        Article bread = new Article("white bread", new DollarAmount(2));
        Article gameboy = new Article("gameboy", new DollarAmount(100));

        ShoppingKart myKart = new ShoppingKart();
        myKart.addItem(cheese, 10);
        myKart.addItem(bread, 2);
        myKart.addItem(gameboy, 1);

        System.out.println(myKart.articlesPrice());
    }

    public static class ShoppingKart {
        private MyList<Article> articles = MyList.nil();


        public void addItem(Article article, int number){
            for(int loop=0; loop<number; ++loop){
                articles = articles.add(article);
            }
        }

        public int articlesPrice(){
            int price = 0;
            for(MyList<Article> l = articles; l!=null; l=l.tail){
                //price += l.elem.price.amount;
            }
            return price;
        }
    }

    public static class MyList<A> {
        private final A elem;
        private final MyList<A> tail;

        public MyList(A elem, MyList<A> tail){
            this.elem = elem;
            this.tail = tail;
        }

        public MyList<A> add(A elem){
            return new MyList<>(
                    elem,
                    this);
        }


        public static <A> MyList<A> nil(){
            return new MyList<>(
                    null,
                    null);
        }
    }

    public static class DollarAmount {
        public final int amount;

        public DollarAmount(int  amount){
            DollarAmount t = this;
            this.amount = amount;
        }
    }

    public static class Article{
        private final String name;
        private final  DollarAmount price;

        public Article(String name, DollarAmount price){
            this.name = name;
            this.price = price;
        }
    }
}
