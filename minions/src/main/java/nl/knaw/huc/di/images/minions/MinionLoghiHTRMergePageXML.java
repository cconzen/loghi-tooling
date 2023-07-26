package nl.knaw.huc.di.images.minions;

import com.google.common.collect.Lists;
import nl.knaw.huc.di.images.imageanalysiscommon.UnicodeToAsciiTranslitirator;
import nl.knaw.huc.di.images.layoutds.models.HTRConfig;
import nl.knaw.huc.di.images.layoutds.models.Page.*;
import nl.knaw.huc.di.images.pagexmlutils.PageUtils;
import nl.knaw.huc.di.images.pagexmlutils.StyledString;
import nl.knaw.huc.di.images.stringtools.StringTools;
import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;
import org.elasticsearch.common.Strings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MinionLoghiHTRMergePageXML extends BaseMinion implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(MinionLoghiHTRMergePageXML.class);

    private final Map<String, String> fileTextLineMap;
    private final Consumer<PcGts> pageSaver;
    private final String pageFileName;
    private final Map<String, Double> confidenceMap;
    private final HTRConfig htrConfig;
    private final UnicodeToAsciiTranslitirator unicodeToAsciiTranslitirator;
    private final String identifier;
    private final Supplier<PcGts> pageSupplier;
    private final String comment;

    public MinionLoghiHTRMergePageXML(String identifier, Supplier<PcGts> pageSupplier, HTRConfig htrConfig,
                                      Map<String, String> fileTextLineMap, Map<String, Double> confidenceMap,
                                      Consumer<PcGts> pageSaver, String pageFileName, String comment) {
        this.identifier = identifier;
        this.pageSupplier = pageSupplier;
        this.htrConfig = htrConfig;
        this.confidenceMap = confidenceMap;
        this.fileTextLineMap = fileTextLineMap;
        this.pageSaver = pageSaver;
        this.pageFileName = pageFileName;
        this.comment = comment;
        unicodeToAsciiTranslitirator = new UnicodeToAsciiTranslitirator();
    }

    private void runFile(Supplier<PcGts> pageSupplier) throws IOException {
        LOG.info(identifier + " processing...");
        PcGts page = pageSupplier.get();

        if (page == null) {
            LOG.error("Could not read page for {}.", identifier);
            return;
        }

        for (TextRegion textRegion : page.getPage().getTextRegions()) {
            for (TextLine textLine : textRegion.getTextLines()) {
                String text = fileTextLineMap.get(pageFileName + "-" + textLine.getId());
                if (text == null) {
                    continue;
                }

                TextLineCustom textLineCustom = new TextLineCustom();
                final StyledString styledString = StyledString.fromStringWithStyleCharacters(text);
                styledString.getStyles().forEach(style -> textLineCustom.addCustomTextStyle(style.getStyles(), style.getOffset(), style.getLength()));
                final String cleanText = styledString.getCleanText();

                Double confidence = confidenceMap.get(pageFileName + "-" + textLine.getId());
                textLine.setTextEquiv(new TextEquiv(confidence, unicodeToAsciiTranslitirator.toAscii(cleanText), cleanText));
                textLine.setWords(new ArrayList<>());
                textLine.setCustom(textLineCustom.toString());
            }
        }
        page.getMetadata().setLastChange(new Date());
        if (this.comment != null) {
            String newComment = this.comment;
            if (!Strings.isNullOrEmpty(page.getMetadata().getComments())){
                newComment = page.getMetadata().getComments()+"; " + this.comment;
            }
            page.getMetadata().setComments(newComment);
        }

        MetadataItem metadataItem = createProcessingStep(htrConfig);
        if (page.getMetadata().getMetadataItems() == null) {
            page.getMetadata().setMetadataItems(new ArrayList<>());
        }
        page.getMetadata().getMetadataItems().add(metadataItem);

        pageSaver.accept(page);
    }


    private MetadataItem createProcessingStep(HTRConfig htrConfig) {
        MetadataItem metadataItem = new MetadataItem();
        metadataItem.setType("processingStep");
        metadataItem.setName("htr");
        metadataItem.setValue("loghi-htr");
        Labels labels = new Labels();
        ArrayList<Label> labelsList = new ArrayList<>();
        final Label githashLabel = new Label();
        githashLabel.setType("githash");
        githashLabel.setValue(htrConfig.getGithash());
        labelsList.add(githashLabel);
        final Label modelLabel = new Label();
        modelLabel.setType("model");
        modelLabel.setValue(htrConfig.getModel());
        labelsList.add(modelLabel);
        final Label uuidLabel = new Label();
        uuidLabel.setType("uuid");
        uuidLabel.setValue(htrConfig.getUuid().toString());
        labelsList.add(uuidLabel);
        for (String key : htrConfig.getValues().keySet()) {
            Label label = new Label();
            label.setType(key);
            Object value = htrConfig.getValues().get(key);
            label.setValue(String.valueOf(value));
            labelsList.add(label);
        }
        labels.setLabel(labelsList);
        metadataItem.setLabels(labels);
        return metadataItem;
    }

    public static Options getOptions() {
        final Options options = new Options();

        options.addOption(Option.builder("input_path").hasArg(true).required(true)
                .desc("Page to be updated with the htr results").build()
        );

        options.addOption(Option.builder("results_file").hasArg(true).required(true)
                .desc("File with the htr results").build()
        );

        options.addOption("config_file", true, "File with the htr config.");

        options.addOption("help", false, "prints this help dialog");

        options.addOption("threads", true, "number of threads to use, default 4");
        options.addOption("comment", true, "custom comments");
        final Option whiteListOption = Option.builder("config_white_list").hasArgs()
                .desc("a list with properties that should be added to the PageXML")
                .build();
        options.addOption(whiteListOption);

        return options;
    }

    public static void main(String[] args) throws Exception {
        int numthreads = Runtime.getRuntime().availableProcessors();
        numthreads = 4;
        Path inputPath = Paths.get("/media/rutger/DIFOR1/data/1.05.14/83/page");
        String resultsFile = "/tmp/output/results.txt";
        String configFile = null;
        String comment = null;
        final Options options = getOptions();
        final CommandLineParser parser = new DefaultParser();
        final CommandLine commandLine;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException ex) {
            printHelp(options, "java " + MinionLoghiHTRMergePageXML.class.getName());
            return;
        }

        if (commandLine.hasOption("help")) {
            printHelp(options, "java " + MinionLoghiHTRMergePageXML.class.getName());
            return;
        }

        inputPath = Paths.get(commandLine.getOptionValue("input_path"));
        resultsFile = commandLine.getOptionValue("results_file");

        if (commandLine.hasOption("config_file")) {
            configFile = commandLine.getOptionValue("config_file");
        }

        if (commandLine.hasOption("threads")) {
            numthreads = Integer.parseInt(commandLine.getOptionValue("threads"));
        }

        if (commandLine.hasOption("comment")) {
            comment = commandLine.getOptionValue("comment");
        }

        final List<String> configWhiteList;
        if (commandLine.hasOption("config_white_list")) {
            configWhiteList = Arrays.asList(commandLine.getOptionValues("config_white_list"));
        } else {
            configWhiteList = Lists.newArrayList("batch_size");
        }


        ExecutorService executor = Executors.newFixedThreadPool(numthreads);

        HTRConfig htrConfig = readConfigFile(configFile, configWhiteList);

        final HashMap<String, String> fileTextLineMap = new HashMap<>();
        final HashMap<String, Double> confidenceMap = new HashMap<>();

        fillDictionary(resultsFile, fileTextLineMap, confidenceMap);
        if (!Files.exists(inputPath)) {
            LOG.error("input path does not exist: " + inputPath.toAbsolutePath());
            System.exit(1);
        }
        DirectoryStream<Path> fileStream = Files.newDirectoryStream(inputPath);
        List<Path> files = new ArrayList<>();
        fileStream.forEach(files::add);
        files.sort(Comparator.comparing(Path::toString));

        for (Path file : files) {
            if (file.toString().endsWith(".xml")) {
                Consumer<PcGts> pageSaver = page -> {
                    try {
                        String pageXmlString = PageUtils.convertPcGtsToString(page);
                        StringTools.writeFile(file.toAbsolutePath().toString(), pageXmlString);
                    } catch (IOException e) {
                        LOG.error("Could not save page: {}", file.toAbsolutePath());
                    }
                };

                final String pageFileName = FilenameUtils.removeExtension(file.getFileName().toString());
                Supplier<PcGts> pageSupplier = () -> {
                    try {
                        return PageUtils.readPageFromFile(file);
                    } catch (IOException e) {
                        LOG.error("Could not load page: {}", file.toAbsolutePath());
                        return null;
                    }
                };

                Runnable worker = new MinionLoghiHTRMergePageXML(pageFileName, pageSupplier, htrConfig, fileTextLineMap,
                        confidenceMap, pageSaver, pageFileName, comment);
                executor.execute(worker);
            }
        }


        executor.shutdown();
        while (!executor.isTerminated()) {
        }
    }

    public static HTRConfig readConfigFile(String configFile, List<String> configWhiteList) throws IOException, org.json.simple.parser.ParseException {
        HTRConfig htrConfig = new HTRConfig();
        if (Strings.isNullOrEmpty(configFile) || !Files.exists(Paths.get(configFile))) {
            return htrConfig;
        }
        JSONObject jsonObject = (JSONObject) new JSONParser().parse(new FileReader(configFile));

        String gitHash = jsonObject.get("git_hash").toString();
        String model = jsonObject.get("model").toString();

        htrConfig.setModel(model);
        htrConfig.setGithash(gitHash);

        Map<String, Object> values = new HashMap<>();

        JSONObject args = (JSONObject) jsonObject.get("args");
        for (Object key : args.keySet()) {
            LOG.debug(String.valueOf(key));
            LOG.debug(String.valueOf(args.get(key)));
            if (args.get(key) != null && configWhiteList.contains(key)) {
                values.put((String) key, String.valueOf(args.get(key)));
            }
        }
        htrConfig.setValues(values);

        return htrConfig;
    }

    private static void fillDictionary(String resultsFile, Map<String, String> fileTextLineMap, Map<String, Double> confidenceMap) throws IOException {

        try (BufferedReader br = new BufferedReader(new FileReader(resultsFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] splitted = line.split("\t");
                String filename = splitted[0];
                double confidence = 0;

                try {
                    confidence = Double.parseDouble(splitted[1]);
                } catch (Exception ex) {
                    LOG.error(filename, ex);
                }
                StringBuilder text = new StringBuilder();
                for (int i = 2; i < splitted.length; i++) {
                    text.append(splitted[i]);//line.substring(filename.length() + 1);
                    text.append("\t");
                }
                text = new StringBuilder(text.toString().trim());
                splitted = filename.split("/");
                filename = splitted[splitted.length - 1].replace(".png", "").trim();
                fileTextLineMap.put(filename, text.toString().trim());
                confidenceMap.put(filename, confidence);
                LOG.debug(filename + " appended to dictionary");
            }
        }
    }

    @Override
    public void run() {
        try {
            this.runFile(this.pageSupplier);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
