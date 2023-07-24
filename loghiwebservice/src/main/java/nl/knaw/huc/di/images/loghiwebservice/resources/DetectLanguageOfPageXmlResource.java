package nl.knaw.huc.di.images.loghiwebservice.resources;

import nl.knaw.huc.di.images.layoutds.models.Page.PcGts;
import nl.knaw.huc.di.images.minions.MinionDetectLanguageOfPageXml;
import nl.knaw.huc.di.images.pagexmlutils.PageUtils;
import nl.knaw.huc.di.images.stringtools.StringTools;
import nl.knaw.huygens.pergamon.nlp.langident.Model;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Path("detect-language-of-page-xml")
public class DetectLanguageOfPageXmlResource {
    public static final Logger LOG = LoggerFactory.getLogger(DetectLanguageOfPageXmlResource.class);
    private final String uploadLocation;
    private final ExecutorService executorService;
    private final Supplier<String> queueUsageStatusSupplier;
    private StringBuilder errorLog;

    public DetectLanguageOfPageXmlResource(String uploadLocation, ExecutorService executorService, Supplier<String> queueUsageStatusSupplier) {

        this.uploadLocation = uploadLocation;
        this.executorService = executorService;
        this.queueUsageStatusSupplier = queueUsageStatusSupplier;
        this.errorLog = new StringBuilder();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response schedule(FormDataMultiPart form) {
        final Map<String, List<FormDataBodyPart>> fields = form.getFields();

        if (!fields.containsKey("training_data")) {
            return missingFieldResponse("training_data");
        }

        if (!fields.containsKey("identifier")) {
            return missingFieldResponse("identifier");
        }

        if (!fields.containsKey("page")) {
            return missingFieldResponse("page");
        }

        Map<String, String> trainingData = new HashMap<>();
        fields.get("training_data").forEach(trainingFile -> {
            final String fileName = trainingFile.getFormDataContentDisposition().getFileName();
            final String language = FilenameUtils.removeExtension(fileName);
            String data;
            try {
                data = new String(trainingFile.getValueAs(InputStream.class).readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.error("Could not process training data for: {}", fileName, e);
                data = "";
            }

            trainingData.put(language, data);
        });

        final Model model = MinionDetectLanguageOfPageXml.trainModel(trainingData);

        FormDataBodyPart xmlUpload = form.getField("page");
        InputStream xmlInputStream = xmlUpload.getValueAs(InputStream.class);
        FormDataContentDisposition xmlContentDispositionHeader = xmlUpload.getFormDataContentDisposition();
        String pageFile = xmlContentDispositionHeader.getFileName();

        final String xml_string;
        try {
            xml_string = IOUtils.toString(xmlInputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Response.serverError().entity("{\"message\":\"Could not read page xml\"}").build();
        }

        Supplier<PcGts> pageSupplier = () -> PageUtils.readPageFromString(xml_string);

        final String identifier = form.getField("identifier").getValue();
        final Consumer<PcGts> pageSaver = page -> {
            final java.nio.file.Path targetFile = Paths.get(uploadLocation, identifier, pageFile);
            try {
                if (!Files.exists(targetFile.getParent())) {
                    Files.createDirectories(targetFile.getParent());
                }
                PageUtils.writePageToFileAtomic(page, targetFile);
            } catch (IOException e) {
                LOG.error("Could not save page: {}", targetFile, e);
                errorLog.append("Could not save page: ").append(targetFile).append("\n");
            }
        };

        final MinionDetectLanguageOfPageXml job = new MinionDetectLanguageOfPageXml(identifier, pageSupplier, pageSaver, model);
        executorService.execute(job);

        return Response.ok("{\"queueStatus\": "+ queueUsageStatusSupplier.get() + "}").build();
    }

    private Response missingFieldResponse(String field) {
        return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\":\"missing field \\\"" + field + "\\\"\"}").build();
    }
}
