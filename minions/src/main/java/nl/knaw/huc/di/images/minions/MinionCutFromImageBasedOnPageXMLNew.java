package nl.knaw.huc.di.images.minions;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import nl.knaw.huc.di.images.imageanalysiscommon.StringConverter;
import nl.knaw.huc.di.images.layoutanalyzer.layoutlib.BinaryLineStrip;
import nl.knaw.huc.di.images.layoutanalyzer.layoutlib.LayoutProc;
import nl.knaw.huc.di.images.layoutanalyzer.layoutlib.OpenCVWrapper;
import nl.knaw.huc.di.images.layoutds.models.Page.PcGts;
import nl.knaw.huc.di.images.layoutds.models.Page.TextLine;
import nl.knaw.huc.di.images.layoutds.models.Page.TextRegion;
import nl.knaw.huc.di.images.layoutds.models.Page.TextStyle;
import nl.knaw.huc.di.images.pagexmlutils.GroundTruthTextLineFormatter;
import nl.knaw.huc.di.images.pagexmlutils.PageUtils;
import nl.knaw.huc.di.images.pipelineutils.ErrorFileWriter;
import nl.knaw.huc.di.images.stringtools.StringTools;
import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;


/*
    this Minion just cuts
 */
public class MinionCutFromImageBasedOnPageXMLNew extends BaseMinion implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(MinionCutFromImageBasedOnPageXMLNew.class);
    public static final int DEFAULT_MINIMUM_INTERLINE_DISTANCE = 35;
    final static double SHRINK_FACTOR = 4;

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private final String outputBase;
    private final String imageFileName;
    private final boolean overwriteExistingPage;
    private final int minWidth;
    private final int minHeight;
    private final int minWidthToHeight;
    private final String outputType;
    private final int channels;
    private final boolean writeTextContents;
    private final Integer rescaleHeight;
    private final boolean outputConfFile;
    private final boolean outputBoxFile;
    private final boolean outputTxtFile;
    private final boolean recalculateTextLineContoursFromBaselines;
    private final int minimumXHeight;
    private final boolean useDiforNames;
    private final boolean writeDoneFiles;
    private final boolean ignoreDoneFiles;
    private final Consumer<String> errorLog;
    private final Consumer<PcGts> pageSaver;
    private final Runnable doneFileWriter;
    private final Integer fixedXHeight;
    private final String identifier;
    private final Supplier<Mat> imageSupplier;
    private final Supplier<PcGts> pageSupplier;
    private final boolean includeTextStyles;
    private final boolean useTags;
    private final boolean skipUnclear;
    private final Double minimumConfidence;

    private final int minimumInterlineDistance;
    private final Optional<ErrorFileWriter> errorFileWriter;

    public MinionCutFromImageBasedOnPageXMLNew(String identifier, Supplier<Mat> imageSupplier,
                                               Supplier<PcGts> pageSupplier, String outputBase,
                                               String imageFileName, boolean overwriteExistingPage,
                                               int minWidth, int minHeight, int minWidthToHeight, String outputType,
                                               int channels, boolean writeTextContents, Integer rescaleHeight,
                                               boolean outputConfFile, boolean outputBoxFile, boolean outputTxtFile,
                                               boolean recalculateTextLineContoursFromBaselines,
                                               Integer fixedXHeight, int minimumXHeight, boolean useDiforNames,
                                               boolean writeDoneFiles, boolean ignoreDoneFiles,
                                               Consumer<String> errorLog, boolean includeTextStyles, boolean useTags,
                                               boolean skipUnclear, Double minimumConfidence,
                                               int minimumInterlineDistance,
                                               Optional<ErrorFileWriter> errorFileWriter) {
        this(identifier, imageSupplier, pageSupplier, outputBase, imageFileName, overwriteExistingPage, minWidth,
                minHeight, minWidthToHeight, outputType, channels, writeTextContents, rescaleHeight, outputConfFile,
                outputBoxFile,
                outputTxtFile, recalculateTextLineContoursFromBaselines, fixedXHeight, minimumXHeight, useDiforNames,
                writeDoneFiles, ignoreDoneFiles, errorLog, page -> {}, () ->{}, includeTextStyles, useTags, skipUnclear,
                minimumConfidence, minimumInterlineDistance, errorFileWriter);
    }

    public MinionCutFromImageBasedOnPageXMLNew(String identifier, Supplier<Mat> imageSupplier,
                                               Supplier<PcGts> pageSupplier, String outputBase, String imageFileName,
                                               boolean overwriteExistingPage,
                                               int minWidth, int minHeight, int minWidthToHeight, String outputType,
                                               int channels, boolean writeTextContents, Integer rescaleHeight,
                                               boolean outputConfFile,
                                               boolean outputBoxFile, boolean outputTxtFile,
                                               boolean recalculateTextLineContoursFromBaselines,
                                               Integer fixedXHeight, int minimumXHeight, boolean useDiforNames,
                                               boolean writeDoneFiles, boolean ignoreDoneFiles,
                                               Consumer<String> errorLog, Consumer<PcGts> pageSaver,
                                               Runnable doneFileWriter, boolean includeTextStyles, boolean useTags,
                                               boolean skipUnclear, Double minimumConfidence, int minimumInterlineDistance,
                                               Optional<ErrorFileWriter> errorFileWriter) {
        this.identifier = identifier;
        this.imageSupplier = imageSupplier;
        this.pageSupplier = pageSupplier;
        this.outputBase = outputBase;
        this.imageFileName = imageFileName;
        this.overwriteExistingPage = overwriteExistingPage;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.minWidthToHeight = minWidthToHeight;
        this.outputType = outputType;
        this.channels = channels;
        this.writeTextContents = writeTextContents;
        this.rescaleHeight = rescaleHeight;
        this.outputConfFile = outputConfFile;
        this.outputBoxFile = outputBoxFile;
        this.outputTxtFile = outputTxtFile;
        this.recalculateTextLineContoursFromBaselines = recalculateTextLineContoursFromBaselines;
        this.fixedXHeight = fixedXHeight;
        this.minimumXHeight = minimumXHeight;
        this.useDiforNames = useDiforNames;
        this.writeDoneFiles = writeDoneFiles;
        this.ignoreDoneFiles = ignoreDoneFiles;
        this.errorLog = errorLog;
        this.pageSaver = pageSaver;
        this.doneFileWriter = doneFileWriter;
        this.includeTextStyles = includeTextStyles;
        this.useTags = useTags;
        this.skipUnclear = skipUnclear;
        this.minimumConfidence = minimumConfidence;
        this.minimumInterlineDistance = minimumInterlineDistance;
        this.errorFileWriter = errorFileWriter;
    }

    private static Options getOptions() {
        Options options = new Options();
// -input_path /media/rutger/DIFOR1/data/republicready/385578/3116_1586_2/ -outputbase /media/rutger/DIFOR1/data/republicready/snippets/ -output_type png -channels 4 -write_text_contents
        options.addOption(Option.builder("input_path").required(true).hasArg(true)
                .desc("Directory that contains the images.").build()
        );
        options.addOption(Option.builder("outputbase").required(true).hasArg(true)
                .desc("outputbase. Base output where imagesnippets will be stored").build()
        );
        options.addOption("output_type", true, "jpg or png, default png");
        options.addOption("channels", true, "3 (jpg/png) or 4 (png), default 4");
        options.addOption("threads", true, "number of threads to use, default 1");
        options.addOption("write_text_contents", false, "default false. Use when generating snippets from ground truth");
        options.addOption("xheight", true, "fixed x-height to use. This can help when used on multiple pages that contain text of very similar height.");
        options.addOption("minimum_xheight", true, "minimum x-height to use.");
        options.addOption("rescaleheight", true, "rescale height");
        options.addOption("min_width", true, "minimum width of baseline");
        options.addOption("difor_names", false, "use the name convention used in the Digital Forensics project");
        options.addOption("page_path", true, "folder that contains the page xml files, by default input_path/page will be used");
        options.addOption("no_page_update", false, "do not update existing page");
        options.addOption("write_done", true, "write done files for images that are processed (default true)");
        options.addOption("ignore_done", false, "ignore done files and (re)process all images");
        options.addOption("copy_font_file", false, "Move the font file if it exists");
        options.addOption("help", false, "prints this help dialog");
        options.addOption("include_text_styles", false, "include text styles in output as special characters");
        options.addOption("use_tags", false, "use tags instead of special characters");
        options.addOption("no_text_line_contour_recalculation", false, "recalculate textline contours based on the baseline");
        options.addOption("skip_unclear", false, "skip lines containing 'unclear' tag. In general set this when training, but not for inferencing");
        options.addOption("use_2013_namespace", "set PageXML namespace to 2013, to avoid causing problems with Transkribus");
        options.addOption("minimum_confidence", true, "minimum confidence for a textline to be included in the output. Default null, meaning include all textlines");
        options.addOption("minimum_interlinedistance", true, "Minimum interlinedistance, default 35");
        options.addOption("output_confidence_file", false, "output confidence files");

        return options;
    }

    public static void main(String[] args) throws Exception {
        int numthreads = 1;
        Path inputPath = Paths.get("/media/rutger/DIFOR1/data/1.05.14/83/");
        String outputBase = "/tmp/output/imagesnippets/";
        boolean overwriteExistingPage = true;
        int minHeight = 5;
        int minWidth = 5;
        int minWidthToHeight = 0;
        Integer rescaleHeight = null;
        String outputType = "png";
        int channels = 4;
        boolean writeTextContents = false;
        boolean outputConfFile = false;
        boolean outputBoxFile = true;
        boolean outputTxtFile = true;
        boolean diforNames;
        boolean recalculateTextLineContoursFromBaselines;
        boolean writeDoneFiles = true;
        boolean ignoreDoneFiles;
        boolean copyFontFile = false;
        boolean includeTextStyles = false;
        boolean useTags = false;
        boolean skipUnclear = false;
        Double minimumConfidence = 0.0;
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            printHelp(options, "java " + MinionCutFromImageBasedOnPageXMLNew.class.getName());
            return;
        }

        if (commandLine.hasOption("help")) {
            printHelp(options, "java " + MinionCutFromImageBasedOnPageXMLNew.class.getName());
            return;
        }

        if (commandLine.hasOption("input_path")) {
            inputPath = Paths.get(commandLine.getOptionValue("input_path"));
        }
        if (commandLine.hasOption("outputbase")) {
            outputBase = commandLine.getOptionValue("outputbase");
        }
        if (commandLine.hasOption("output_type")) {
            outputType = commandLine.getOptionValue("output_type");
        }
        if (commandLine.hasOption("channels")) {
            channels = Integer.parseInt(commandLine.getOptionValue("channels"));
        }
        if (commandLine.hasOption("write_text_contents")) {
            writeTextContents = true;
        }
        Integer fixedXHeight = null;
        if (commandLine.hasOption("xheight")) {
            fixedXHeight = Integer.parseInt(commandLine.getOptionValue("xheight"));
        }
        int minimumXHeight = LayoutProc.MINIMUM_XHEIGHT;
        if (commandLine.hasOption("minimum_xheight")) {
            minimumXHeight = Integer.parseInt(commandLine.getOptionValue("minimum_xheight"));
        }

        if (commandLine.hasOption("threads")) {
            numthreads = Integer.parseInt(commandLine.getOptionValue("threads"));
        }
        if (commandLine.hasOption("rescaleheight")) {
            rescaleHeight = Integer.parseInt(commandLine.getOptionValue("rescaleheight"));
        }

        if (commandLine.hasOption("min_width")) {
            minWidth = Integer.parseInt(commandLine.getOptionValue("min_width"));
        }

        final Path pagePath;
        if (commandLine.hasOption("page_path")) {
            pagePath = Paths.get(commandLine.getOptionValue("page_path"));
        } else {
            pagePath = inputPath.resolve("page");
        }

        if (commandLine.hasOption("no_page_update")) {
            overwriteExistingPage = false;
        }

        if (commandLine.hasOption("write_done")) {
             writeDoneFiles = "true".equals(commandLine.getOptionValue("write_done"));
        }

        if (commandLine.hasOption("copy_font_file")) {
            copyFontFile = true;
        }

        if (commandLine.hasOption("include_text_styles")) {
            includeTextStyles = true;
        }

        if (commandLine.hasOption("use_tags")) {
            useTags = true;
            // Provide warning if include_text_styles is not true since it requires the text styles for conversion
            if (!includeTextStyles){
                LOG.warn("-use_tags is used without -include_text_styles, this will yield plain text. " +
                        "Please pass -include_text_styles as well to ensure html-tag results.");
            }
        }

        if (commandLine.hasOption("skip_unclear")) {
            skipUnclear = true;
        }
        if (commandLine.hasOption("minimum_confidence")) {
            minimumConfidence = Double.parseDouble(commandLine.getOptionValue("minimum_confidence"));
        }

        int minimumInterlineDistance = DEFAULT_MINIMUM_INTERLINE_DISTANCE;
        if (commandLine.hasOption("minimum_interlinedistance")) {
            minimumInterlineDistance = Integer.parseInt(commandLine.getOptionValue("minimum_interlinedistance"));
        }



        ignoreDoneFiles = commandLine.hasOption("ignore_done");

        diforNames = commandLine.hasOption("difor_names");

        recalculateTextLineContoursFromBaselines = !commandLine.hasOption("no_text_line_contour_recalculation");

        outputConfFile = commandLine.hasOption("output_confidence_file");
        String namespace = commandLine.hasOption("use_2013_namespace") ? PageUtils.NAMESPACE2013: PageUtils.NAMESPACE2019;


        ExecutorService executor = Executors.newFixedThreadPool(numthreads);

        DirectoryStream<Path> fileStream = Files.newDirectoryStream(inputPath);
        List<Path> files = new ArrayList<>();
        fileStream.forEach(files::add);
        files.sort(Comparator.comparing(Path::toString));

        for (Path imageFile : files) {
            if (!hasImageExtension(imageFile)) {
                LOG.info("Ignore file '{}', not an image", imageFile);
                continue;
            }

            Supplier<Mat> imageSupplier = () -> {
                String inputFile = imageFile.toAbsolutePath().toString();
                return OpenCVWrapper.imread(inputFile);
            };
            final String identifier = FilenameUtils.removeExtension(imageFile.getFileName().toString());
            final String pageFileName = identifier + ".xml";
            final Path pageFile = pagePath.resolve(pageFileName);

            if (Files.notExists(pageFile)){
                LOG.error(pageFile + " does not exist. Continuing");
                continue;
            }

            Supplier<PcGts> pageSupplier = () -> {
                try {
                    PcGts page = PageUtils.readPageFromFile(pageFile);
                    if (page ==null){
                        LOG.error(pageFile + " does not appear to be a valid PageXml file. It is null");
                        return null;
                    }
                    return page;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    LOG.error(pageFile + " does not appear to be a valid PageXml file: " + ex.getMessage());
                    return null;
                }
            };

            Consumer<PcGts> pageSaver = page -> {
                try {
                    PageUtils.writePageToFile(page,namespace, pageFile);
                } catch (IOException e) {
                    LOG.error("Could not save page", e);
                } catch (TransformerException e) {
                    LOG.error("Could not transform page to 2013 version", e);
                }
            };

            Runnable doneFileWriter = () -> {
                try {
                    StringTools.writeFile(imageFile + ".done", "");
                } catch (IOException e) {
                    LOG.error("Could not write done file.", e);
                }
            };

            /* HACK Move the copy here so I don't have to do it in the actual minion cutting */
            if (copyFontFile) {
                if (!new File(outputBase).exists()) {
                    if (!new File(outputBase).mkdir()){
                        LOG.error(identifier+" could not create outputdir: " + outputBase);
                    }
                }
                String fileNameWithoutExtension = FilenameUtils.removeExtension(imageFile.getFileName().toString());
                File copyInputFile = new File(imageFile.getParent().toFile(), fileNameWithoutExtension + "_font.txt");
                File copyOutputFile = new File(outputBase, fileNameWithoutExtension + "_font.txt");
                Files.copy(copyInputFile.toPath(), copyOutputFile.toPath(), StandardCopyOption.REPLACE_EXISTING) ;
            }

            Runnable worker = new MinionCutFromImageBasedOnPageXMLNew(identifier, imageSupplier, pageSupplier,
                    outputBase, imageFile.getFileName().toString(), overwriteExistingPage,
                    minWidth, minHeight, minWidthToHeight, outputType, channels, writeTextContents, rescaleHeight,
                    outputConfFile, outputBoxFile, outputTxtFile, recalculateTextLineContoursFromBaselines,
                    fixedXHeight, minimumXHeight, diforNames, writeDoneFiles, ignoreDoneFiles, error -> {}, pageSaver,
                    doneFileWriter, includeTextStyles, useTags, skipUnclear, minimumConfidence,
                    minimumInterlineDistance, Optional.empty());
            executor.execute(worker);
        }


        executor.shutdown();
        executor.awaitTermination(60L, TimeUnit.MINUTES);
    }

    private static boolean hasImageExtension(Path file){
        String lowercase = file.toString().toLowerCase();
        return lowercase.endsWith(".jpg")
                || lowercase.endsWith(".jpeg")
                || lowercase.endsWith(".png")
                || lowercase.endsWith(".tif")
                || lowercase.endsWith(".tiff");
    }
    private void runFile(Supplier<Mat> imageSupplier) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        String fileNameWithoutExtension = FilenameUtils.removeExtension(imageFileName);
        File balancedOutputBase = new File (outputBase, fileNameWithoutExtension);

        Mat image;
        LOG.debug(identifier + " processing...");
        image = imageSupplier.get();
        if (!new File(outputBase).exists()) {
            if (!new File(outputBase).mkdir()){
                LOG.error(identifier+" could not create outputdir: " + outputBase);
            }
        }

        if (image.size().width == 0 || image.size().height == 0) {
            LOG.error(identifier + " broken image");
            Files.createDirectory(Paths.get(balancedOutputBase + "/" + fileNameWithoutExtension));
            errorFileWriter.ifPresent(errorWriter -> errorWriter.writeToFile(balancedOutputBase + "/" + fileNameWithoutExtension + "/processing.error", "broken image"));
            return;
        }

        File balancedOutputBaseTmp = Files.createTempDirectory(balancedOutputBase.toPath().getParent(), "." + balancedOutputBase.toPath().getFileName()).toFile();

        PcGts page = this.pageSupplier.get();
        if (page==null){
            LOG.error(identifier + " Page is null");
            errorFileWriter.ifPresent(errorWriter -> errorWriter.writeToFile(balancedOutputBase + "/" + fileNameWithoutExtension + "/processing.error", identifier + " Page is null"));
            return;
        }

        final Stopwatch recalc = Stopwatch.createStarted();
        // resize image

        if (recalculateTextLineContoursFromBaselines) {
            LayoutProc.recalculateTextLineContoursFromBaselines(imageSupplier.toString(), image, page, SHRINK_FACTOR, minimumInterlineDistance);
        }
        LOG.debug(identifier + "recalc: " + recalc.stop());

        for (TextRegion textRegion : page.getPage().getTextRegions()) {
            for (TextLine textLine : textRegion.getTextLines()) {
                if (this.skipUnclear && textLine.getCustom()!=null && textLine.getCustom().contains("unclear")){
                    continue;
                }

                if (minimumConfidence != null){
                    if (textLine.getTextEquiv()!=null && textLine.getTextEquiv().getConf() != null
                            && Double.parseDouble(textLine.getTextEquiv().getConf()) < minimumConfidence) {
                        continue;
                    }
                }

                List<Point> contourPoints = StringConverter.stringToPoint(textLine.getCoords().getPoints());
                if (contourPoints.size() == 0) {
                    //TODO: this should not abort the flow
                    continue;
                }
                List<Point> baseLinePoints = StringConverter.stringToPoint(textLine.getBaseline().getPoints());
                Integer xHeight = null;
                TextStyle textStyle = textLine.getTextStyle();
                if (textStyle != null) {
                    xHeight = textStyle.getxHeight();
                }

                if (fixedXHeight != null) {
                    xHeight = fixedXHeight;
                }
                if (xHeight == null || xHeight < minimumXHeight) {
                    xHeight = minimumXHeight;
                }
                // TODO: determine xheight by histogram
                // TODO: determin xheight by CoCo (printed/printlike only)
                // TODO determine xheight by moving entire baseline up and counting binary pixels
                // TODO: determine xheight by smearing
                boolean includeMask = this.channels == 4;
                String lineStripId = identifier + "-" + textLine.getId();
                BinaryLineStrip binaryLineStrip = LayoutProc.getBinaryLineStrip(imageSupplier.toString(), image, contourPoints,
                        baseLinePoints, xHeight, includeMask, minWidth, lineStripId, 4, 3, 2);
                Mat lineStrip = null;
                if (binaryLineStrip != null && binaryLineStrip.getLineStrip() != null) {
                    lineStrip = binaryLineStrip.getLineStrip();
                    xHeight = binaryLineStrip.getxHeight();
                    if (textLine.getTextStyle() == null) {
                        textLine.setTextStyle(new TextStyle());
                    }
                    textLine.getTextStyle().setxHeight(xHeight);
                    if (lineStrip.width() >= minWidth
                            && lineStrip.height() > minHeight
                            && (lineStrip.width() / lineStrip.height()) >= minWidthToHeight) {
//                            String randomUUIDString = inputXmlFilePath.getFileName().toString() + "-" + textRegion.getId()+"-"+textLine.getId();
                        if (rescaleHeight != null) {
                            Mat binaryLineStripNew = new Mat();
                            double targetHeight = rescaleHeight;
                            double heightScale = targetHeight / (double) (lineStrip.height());
                            double newWidth = heightScale * lineStrip.width();
                            if (newWidth < 32) {
                                newWidth = 32;
                            }
                            Imgproc.resize(lineStrip, binaryLineStripNew, new Size(newWidth, targetHeight));
                            lineStrip = OpenCVWrapper.release(lineStrip);
                            lineStrip = binaryLineStripNew;
                        }
                        if (writeTextContents) {
                            String textValue = GroundTruthTextLineFormatter.getFormattedTextLineStringRepresentation(textLine, includeTextStyles, useTags);
                            if (Strings.isNullOrEmpty(textValue)) {
                                LOG.warn(identifier + " empty line " + textLine.getId());
                                continue;
                            }

                            if (lineStrip.width() > minWidth
                                    && lineStrip.height() > minHeight
                                    && (lineStrip.width() / lineStrip.height()) >= minWidthToHeight) {
                                if (outputTxtFile) {
                                    StringTools.writeFile(new File(balancedOutputBaseTmp, lineStripId + ".txt").getAbsolutePath(), textValue);
                                }
                                if (outputConfFile) {
                                    String confValue = "1";
                                    if (textLine.getTextEquiv() != null && textLine.getTextEquiv().getConf() != null) {
                                        confValue = textLine.getTextEquiv().getConf();
                                    }
                                    StringTools.writeFile(new File(balancedOutputBaseTmp, lineStripId + ".conf").getAbsolutePath(), confValue);
                                }
                                if (outputBoxFile) {
                                    String boxValue = LayoutProc.convertToBoxFile(lineStrip.height(), lineStrip.width(), StringTools.makeNew(textValue));
                                    StringTools.writeFile(new File(balancedOutputBaseTmp, lineStripId + ".box").getAbsolutePath(), boxValue);
                                }
                            }
                        }
                        if (this.useDiforNames) {
                            final String filename = new File(balancedOutputBaseTmp, "textline_" + fileNameWithoutExtension + "_" + textLine.getId() + "." + this.outputType).getAbsolutePath();
                            LOG.debug(identifier + " save snippet: " + filename);

                            Imgcodecs.imwrite(filename, lineStrip);
                        } else {
                            final String absolutePath = new File(balancedOutputBaseTmp, lineStripId + "." + this.outputType).getAbsolutePath();
                            try {
                                Imgcodecs.imwrite(absolutePath, lineStrip);
                            } catch (Exception e) {
                                errorLog.accept("Cannout write "+ absolutePath);
                                throw e;
                            }
                        }
                    }
                }
                lineStrip = OpenCVWrapper.release(lineStrip);
            }
        }


        if (balancedOutputBase.exists()) {
            deleteFolderRecursively (balancedOutputBase);
        }

        Files.move(balancedOutputBaseTmp.toPath(), balancedOutputBase.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        if (overwriteExistingPage) {
            pageSaver.accept(page);
        }

        image = OpenCVWrapper.release(image);
        LOG.debug(identifier + " single image took: " + stopwatch.elapsed(TimeUnit.MILLISECONDS));

        if (this.writeDoneFiles) {
            this.doneFileWriter.run();
        }
    }

    private void deleteFolderRecursively(File balancedOutputBaseTmp) throws IOException {
        final Path tmpPath = balancedOutputBaseTmp.toPath().getParent().resolve("." + UUID.randomUUID());
        Files.move(balancedOutputBaseTmp.toPath(), tmpPath, StandardCopyOption.ATOMIC_MOVE);
        Files.walkFileTree(tmpPath, new SimpleFileVisitor<>() {

            // delete directories or folders
            @Override
            public FileVisitResult postVisitDirectory(Path dir,
                                                      IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }

            // delete files
            @Override
            public FileVisitResult visitFile(Path file,
                                             BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public void run() {
        try {
            if (this.ignoreDoneFiles || !Files.exists(Paths.get(this.identifier + ".done"))) {
                this.runFile(this.imageSupplier);
            }
        } catch (IOException e) {
            LOG.error("Could not process image {}", this.imageFileName, e);
            errorFileWriter.ifPresent(errorWriter -> errorWriter.write(identifier, e, "Image could not be processed"));
            e.printStackTrace();
        }
    }
}
