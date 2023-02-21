package nl.knaw.huc.di.images.loghiwebservice;

import io.dropwizard.Application;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import nl.knaw.huc.di.images.loghiwebservice.health.TemplateHealthCheck;
import nl.knaw.huc.di.images.loghiwebservice.resources.ExtractBaselinesResource;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;

public class LoghiWebserviceApplication extends Application<LoghiWebserviceConfiguration> {
    public static void main(String[] args) throws Exception {
        new LoghiWebserviceApplication().run(args);
    }

    @Override
    public String getName() {
        return "loghi-webservice";
    }

    @Override
    public void initialize(Bootstrap<LoghiWebserviceConfiguration> bootstrap) {
        bootstrap.addBundle(new MultiPartBundle());
    }

    @Override
    public void run(LoghiWebserviceConfiguration configuration, Environment environment) {
        // TODO move parameters to config
        final ExecutorService extractBaselinesExecutor = environment.lifecycle()
                .executorService("ExtractBaselines")
                .maxThreads(1)
                .workQueue(new ArrayBlockingQueue<Runnable>(1, true))
                .build();
        final ExtractBaselinesResource resource = new ExtractBaselinesResource(extractBaselinesExecutor, "/tmp/upload/");

        final TemplateHealthCheck healthCheck =
                new TemplateHealthCheck(configuration.getTemplate());
        environment.healthChecks().register("template", healthCheck);

        environment.jersey().register(resource);
    }

}