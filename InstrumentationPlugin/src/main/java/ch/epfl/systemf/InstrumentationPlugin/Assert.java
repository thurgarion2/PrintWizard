package ch.epfl.systemf.InstrumentationPlugin;

public class Assert {
    public static void assertThat(boolean condition){
            if(!condition){
                throw new IllegalStateException("an invariant is not respected");
            }
    }
}
