package infrastruktur.batch.javabatch;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class InfJavabatchMain {
    public static void main(String... args) {
        Quarkus.run(JobHookApplication.class, args);
    }
}