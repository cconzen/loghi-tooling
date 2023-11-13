package nl.knaw.huc.di.images.minions;

import com.google.common.base.Strings;
import nl.knaw.huc.di.images.layoutds.models.Page.*;
import nl.knaw.huc.di.images.pagexmlutils.PageUtils;
import nl.knaw.huygens.pergamon.nlp.langident.Model;
import nl.knaw.huygens.pergamon.nlp.langident.NaiveBayes;
import nl.knaw.huygens.pergamon.nlp.langident.TrainingSet;
import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * pathOfTrainingSet expects a folder with text files.
 * The name of the text files will be used as the name of the language.
 * It will be the choice of the user to use the PageXML standard.
 * For more information see the LanguageSimpleType definition of https://github.com/PRImA-Research-Lab/PAGE-XML/blob/master/pagecontent/schema/pagecontent.xsd
 */
public class MinionDetectLanguageOfPageXml implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(MinionDetectLanguageOfPageXml.class);
    public static final int NUM_THREADS = 1;

    private final String identifier;
    private final Supplier<PcGts> pageLoader;
    private final Consumer<PcGts> pageSaver;
    private final Model model;

    /**
     *
     * @param pageLoader
     * @param pageSaver
     * @param model the model to guess the language
     */
    public MinionDetectLanguageOfPageXml(String identifier, Supplier<PcGts> pageLoader, Consumer<PcGts> pageSaver, Model model) {
        this.identifier = identifier;
        this.pageLoader = pageLoader;
        this.pageSaver = pageSaver;
        this.model = model;
    }

    public static Options getOptions() {
        final Options options = new Options();

        options.addOption(Option.builder("page").hasArg(true).desc("Path to the page file or directory with page files")
                .required().build()
        );
        options.addOption(Option.builder("lang_train_data").hasArg(true)
                .desc("Folder that contains training data for language detector (optional). By default Loghi tooling's own files are used.")
                .required(false).build()
        );
        options.addOption("help", false, "prints this help dialog");
        options.addOption("use_2013_namespace", "set PageXML namespace to 2013, to avoid causing problems with Transkribus");

        return options;
    }

    public static void main(String[] args) throws Exception {
        final Options options = getOptions();
        final CommandLineParser commandLineParser = new DefaultParser();
        final CommandLine commandLine;

        try {
            commandLine = commandLineParser.parse(options, args);
        }
        catch (ParseException e) {
            printHelp(options, "java " + MinionDetectLanguageOfPageXml.class.getName());
            return;
        }

        if (commandLine.hasOption("help")) {
            printHelp(options, "java " + MinionDetectLanguageOfPageXml.class.getName());
            return;
        }

        String pathToPageString
                = commandLine.getOptionValue("page");


        final Model model;
        if (commandLine.hasOption("lang_train_data")) {
            String pathOfTrainingSet = commandLine.getOptionValue("lang_train_data");
            model = trainModel(pathOfTrainingSet);
        } else {
            model = trainModelWithDefaultData();
        }
        String namespace = commandLine.hasOption("use_2013_namespace") ? PageUtils.NAMESPACE2013: PageUtils.NAMESPACE2019;


        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

        final Path pagePath = Paths.get(pathToPageString);
        if (Files.isDirectory(pagePath)) {
            DirectoryStream<Path> fileStream = Files.newDirectoryStream(pagePath);
            for (final Path file : fileStream) {
                if (FilenameUtils.getExtension(file.getFileName().toString()).equals("xml")) {
                    final Supplier<PcGts> pageSupplier = () -> {
                        try {
                            return PageUtils.readPageFromFile(file);
                        } catch(IOException e) {
                            LOG.error("Cannot read page file: {}", file.toAbsolutePath(), e);
                            return null;
                        }
                    };

                    Consumer<PcGts> pageSaver = page -> {
                        try {
                            PageUtils.writePageToFile(page, namespace, file);
                        } catch (IOException e) {
                            LOG.error("Cannot save page for: {}", file.toAbsolutePath(), e);
                        } catch (TransformerException e) {
                            LOG.error("Could not transform page to 2013 version", e);
                        }
                    };
                    final MinionDetectLanguageOfPageXml job = new MinionDetectLanguageOfPageXml(file.getFileName().toString(), pageSupplier, pageSaver, model);
                    executor.execute(job);
                }
            }
        } else {
            final Supplier<PcGts> pageSupplier = () -> {
                try {
                    return PageUtils.readPageFromFile(pagePath);
                } catch(IOException e) {
                    LOG.error("Cannot read page file: {}", pagePath.toAbsolutePath(), e);
                    return null;
                }
            };

            Consumer<PcGts> pageSaver = page -> {
                try {
                    PageUtils.writePageToFile(page, namespace, pagePath);
                } catch (IOException e) {
                    LOG.error("Cannot save page for: {}", pagePath.toAbsolutePath(), e);
                } catch (TransformerException e) {
                    LOG.error("Could not transform page to 2013 version", e);
                }
            };
            final MinionDetectLanguageOfPageXml job = new MinionDetectLanguageOfPageXml(pagePath.getFileName().toString(), pageSupplier, pageSaver, model);
            executor.execute(job);
        }

        executor.shutdown();
        executor.awaitTermination(60L, TimeUnit.MINUTES);
    }

    public static void printHelp(Options options, String callName) {
        final HelpFormatter helpFormatter = new HelpFormatter();

        helpFormatter.printHelp(callName, options, true);
    }

    public static Model trainModelWithDefaultData() throws IOException {
        final String defaultTrainingSet = MinionDetectLanguageOfPageXml.class.getResource("/lang-ident-training-data").getPath();
        return trainModel(defaultTrainingSet);
    }

    public void run() {
        LOG.info(identifier + ": processing file...");
        PcGts pcGts = this.pageLoader.get();

        Page page = pcGts.getPage();
        String pageText = "";

        for (TextRegion textRegion : page.getTextRegions()) {
            String textOfRegion = "";
            for (TextLine textLine : textRegion.getTextLines()) {
                TextEquiv textEquiv = textLine.getTextEquiv();
                if (textEquiv != null && !Strings.isNullOrEmpty(textEquiv.getUnicode())) {
                    textOfRegion += textEquiv.getUnicode() + "\n";
                }
            }
            final String languageOfRegion = model.predictBest(textOfRegion);
            textRegion.setPrimaryLanguage(languageOfRegion);

            for (TextLine textLine : textRegion.getTextLines()) {
                TextEquiv textEquiv = textLine.getTextEquiv();
                if (textEquiv != null && textEquiv.getPlainText() != null) {
                    final String languageOfLine = model.predictBest(textEquiv.getPlainText());
                    textLine.setPrimaryLanguage(languageOfLine);
                }
            }
            pageText += textOfRegion;
        }
        String language = model.predictBest(pageText);
        page.setPrimaryLanguage(language);

        pcGts.getMetadata().setLastChange(new Date());
        if (pcGts.getMetadata().getMetadataItems() == null) {
            pcGts.getMetadata().setMetadataItems(new ArrayList<>());
        }
        MetadataItem metadataItem = new MetadataItem();
        metadataItem.setType("processingStep");
        metadataItem.setName("detect-language");
        metadataItem.setValue("loghi-htr-tooling");

        pcGts.getMetadata().getMetadataItems().add(metadataItem);

        pageSaver.accept(pcGts);
    }

    private static Model trainModel(String pathOfTrainingSet) throws IOException {
        TrainingSet trainingSet;
        if (pathOfTrainingSet == null) {
            trainingSet = TrainingSet.getBuiltin();
        } else {
            LOG.info("training data: " + pathOfTrainingSet);
            List<CharSequence> docs = new ArrayList<>();
            final ArrayList<String> labels = new ArrayList<>();
            final File file = new File(pathOfTrainingSet);
            LOG.info("file exists: " + file.exists());
            if (file.isDirectory()) {
                for (File doc : file.listFiles()) {
                    processTrainingFile(docs, labels, doc);
                }
            } else {
                processTrainingFile(docs, labels, file);
            }

            trainingSet = new TrainingSet(docs, labels);
        }
        Model guesser = new NaiveBayes().train(trainingSet);

        return guesser;
    }

    /**
     *
     * @param trainingData map of language name, language example data
     * @return
     */
    public static Model trainModel(Map<String, String> trainingData) {
        final List<CharSequence> docs = new ArrayList<>();
        final List<String> labels = new ArrayList<>();

        for (Map.Entry<String, String> languageExamplePair : trainingData.entrySet()) {
            String language = languageExamplePair.getKey();

             languageExamplePair.getValue().lines().forEach(line -> {
                 docs.add(line);
                 labels.add(language);
             });
        }

        final TrainingSet trainingSet = new TrainingSet(docs, labels);

        return new NaiveBayes().train(trainingSet);
    }



    private static void processTrainingFile(List<CharSequence> docs, ArrayList<String> labels, File doc) throws IOException {
        final List<String> lines = Files.readAllLines(doc.toPath());
        final String language = FilenameUtils.getBaseName(doc.getName());

        for (String line : lines) {
            if (!StringUtils.isBlank(line)) {
                docs.add(line);
                labels.add(language);
            }
        }
    }
}
