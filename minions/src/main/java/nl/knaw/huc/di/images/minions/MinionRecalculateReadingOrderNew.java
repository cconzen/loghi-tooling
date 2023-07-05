package nl.knaw.huc.di.images.minions;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import nl.knaw.huc.di.images.imageanalysiscommon.StringConverter;
import nl.knaw.huc.di.images.imageanalysiscommon.UnicodeToAsciiTranslitirator;
import nl.knaw.huc.di.images.layoutanalyzer.layoutlib.LayoutProc;
import nl.knaw.huc.di.images.layoutds.models.Page.*;
import nl.knaw.huc.di.images.pagexmlutils.PageUtils;
import nl.knaw.huc.di.images.stringtools.StringTools;
import org.apache.commons.cli.*;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


public class MinionRecalculateReadingOrderNew implements Runnable, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MinionRecalculateReadingOrderNew.class);

    public static final UnicodeToAsciiTranslitirator UNICODE_TO_ASCII_TRANSLITIRATOR = new UnicodeToAsciiTranslitirator();
    private final String identifier;
    private final PcGts page;
    private final Consumer<PcGts> pageSaver;
    private final boolean cleanBorders;
    private final int borderMargin;
    private final boolean asSingleRegion;

    public MinionRecalculateReadingOrderNew(String identifier, PcGts page, Consumer<PcGts> pageSaver, boolean cleanBorders, int borderMargin, boolean asSingleRegion) {
        this.identifier = identifier;
        this.page = page;
        this.pageSaver = pageSaver;
        this.cleanBorders = cleanBorders;
        this.borderMargin = borderMargin;
        this.asSingleRegion= asSingleRegion;
    }

    private static Options getOptions() {
        Options options = new Options();

        options.addOption(Option.builder("input_dir").required(true).hasArg(true)
                .desc("directory of the page files that should be processed").build()
        );
        options.addOption("threads", true, "threads to use");
        options.addOption("clean_borders", false, "when true removes the small baselines, that are visible on the piece of the adjacent that is visible in the scan (default value is false)");
        options.addOption("border_margin", true, "border_margin, default 200");
        options.addOption("help", false, "prints this help dialog");
        options.addOption("as_single_region", false, "as single region");

        return options;
    }

    public static void printHelp(Options options, String callName) {
        final HelpFormatter helpFormatter = new HelpFormatter();

        helpFormatter.printHelp(callName, options, true);
    }

    public static void main(String[] args) throws IOException, ParseException {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException ex ) {
            printHelp(options, "java " + MinionRecalculateReadingOrderNew.class.getName());
            return;
        }

        if (cmd.hasOption("help")) {
            printHelp(options, "java " + MinionRecalculateReadingOrderNew.class.getName());
            return;
        }

        String inputDir = "/media/rutger/HDI0002/difor-data-hannah-divide8/page/";
        if (cmd.hasOption("input_dir")) {
            inputDir = cmd.getOptionValue("input_dir");
            LOG.info("input_dir: " + inputDir);
        }
        int numthreads = 2;
        if (cmd.hasOption("threads")) {
            numthreads = Integer.parseInt(cmd.getOptionValue("threads"));
        }
        boolean cleanBorders = cmd.hasOption("clean_borders");
        int borderMargin = 200;
        if (cmd.hasOption("border_margin")) {
            borderMargin = Integer.parseInt(cmd.getOptionValue("border_margin"));
        }

        boolean asSingleRegion = cmd.hasOption("as_single_region");

        ExecutorService executor = Executors.newFixedThreadPool(numthreads);
        DirectoryStream<Path> fileStream = Files.newDirectoryStream(Paths.get(inputDir));
        List<Path> files = new ArrayList<>();
        fileStream.forEach(files::add);
        files.sort(Comparator.comparing(Path::toString));

        for (Path file : files) {
            if (!file.toFile().isFile()) {
                continue;
            }
            if (file.getFileName().toString().endsWith(".xml")) {
                LOG.info(file.toAbsolutePath().toString());
                final String pageFile = file.toAbsolutePath().toString();
                String pcGtsString = StringTools.loadStringFromFile(pageFile);
                PcGts page = PageUtils.readPageFromString(pcGtsString);

                Consumer<PcGts> pageSaver = newPage -> {
                    XmlMapper xmlMapper = new XmlMapper();
                    try {
                        String newPageString = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(newPage);
                        StringTools.writeFile(pageFile, newPageString);
                    } catch (IOException e) {
                        LOG.error("Could not save updated page", e);
                    }
                };

                Runnable worker = new MinionRecalculateReadingOrderNew(pageFile, page, pageSaver, cleanBorders, borderMargin, asSingleRegion);
                executor.execute(worker);//calling execute method of ExecutorService
            }
        }
        executor.shutdown();
        while (!executor.isTerminated()) {

        }
    }

    @Override
    public void close() throws Exception {
    }

//    public static PcGts runPage(DocumentImage documentImage, boolean cleanBorders, int borderMargin) {
//        DocumentOCRResult documentOCRResult = MinionCutFromImageBasedOnPageXML.getLatestGroundtruth(documentImage.getDocumentOCRResults());
//        PcGts currentPage = PageUtils.readPageFromString(documentOCRResult.getResult());
//        return runPage(currentPage, cleanBorders, borderMargin);
//    }

    public static PcGts runPage(String id, PcGts page, boolean cleanBorders, int borderMargin, boolean asSingleRegion) {

        int dubiousSizeWidth = page.getPage().getImageWidth() / 20;
        List<TextLine> allLines = new ArrayList<>();
        for (TextRegion textRegion : page.getPage().getTextRegions()) {
            allLines.addAll(textRegion.getTextLines());
        }
        double interlinemedian = LayoutProc.interlineMedian(allLines);
        LOG.info(id + " interlinemedian: " + interlinemedian);
        if (interlinemedian < 10) {
            interlinemedian = 10;
        }

        List<TextLine> linesToRemove = new ArrayList<>();
        page.getPage().getTextRegions().clear();
        if (cleanBorders) {
            for (TextLine textLine : allLines) {
                List<Point> points = StringConverter.stringToPoint(textLine.getBaseline().getPoints());
                Point textLineStart = points.get(0);
                Point textLineEnd = points.get(points.size() - 1);
                final String dubious_line_at_border = "dubious line at border";
                if (Math.abs(textLineEnd.x - page.getPage().getImageWidth()) < borderMargin) {
                    textLine.setTextEquiv(new TextEquiv(null, UNICODE_TO_ASCII_TRANSLITIRATOR.toAscii("border"), "border"));
                    if (StringConverter.distance(textLineStart, textLineEnd) < dubiousSizeWidth) {
                        textLine.setTextEquiv(new TextEquiv(null, UNICODE_TO_ASCII_TRANSLITIRATOR.toAscii(dubious_line_at_border), dubious_line_at_border));
                        linesToRemove.add(textLine);
                    }
                }
                if (textLineStart.x < borderMargin) {
                    textLine.setTextEquiv(new TextEquiv(null, UNICODE_TO_ASCII_TRANSLITIRATOR.toAscii("border"), "border"));
                    if (StringConverter.distance(textLineStart, textLineEnd) < dubiousSizeWidth) {
                        textLine.setTextEquiv(new TextEquiv(null, UNICODE_TO_ASCII_TRANSLITIRATOR.toAscii(dubious_line_at_border), dubious_line_at_border));
                        linesToRemove.add(textLine);
                    }
                }

            }
            allLines.removeAll(linesToRemove);
        }

        List<TextLine> removedLines = new ArrayList<>();
        for (TextLine textLine : allLines) {
            if (removedLines.contains(textLine)) {
                continue;
            }

            List<TextLine> cluster = new ArrayList<>();
            cluster.add(textLine);
            removedLines.add(textLine);

            boolean newLinesAdded = true;
            while (newLinesAdded) {
                newLinesAdded = false;
                for (TextLine mainTextLine : cluster) {
                    List<Point> mainPoints = StringConverter.stringToPoint(mainTextLine.getBaseline().getPoints());
                    double mainTextLineOrientation = LayoutProc.getMainAngle(mainPoints);
                    Point mainTextLineStart = mainPoints.get(0);
                    Point mainTextLineEnd = mainPoints.get(mainPoints.size() - 1);

                    for (TextLine subTextLine : allLines) {
                        if (removedLines.contains(subTextLine)) {
                            continue;
                        }
                        double subTextLineOrientation = LayoutProc.getMainAngle(StringConverter.stringToPoint(subTextLine.getBaseline().getPoints()));
//                if subTextLine same orientation
//                if (LayoutProc.distance())
//                && closeby
                        List<Point> subPoints = StringConverter.stringToPoint(subTextLine.getBaseline().getPoints());
                        Point subTextLineStart = subPoints.get(0);
                        Point subTextLineEnd = subPoints.get(subPoints.size() - 1);

                        //add to cluster if starts are close together
                        double maxDistance = interlinemedian * 1.5;
                        if (StringConverter.distance(mainTextLineStart, subTextLineStart) < maxDistance) {
                            cluster.add(subTextLine);
                            removedLines.add(subTextLine);
                            newLinesAdded = true;
                        } else {
                            //add to cluster if centers are vertically close together
                            double horizontalSubPointX = ((subTextLineStart.x + subTextLineEnd.x) / 2);
                            double averageSubPointY = ((subTextLineStart.y + subTextLineEnd.y) / 2);
                            double mainTextLineY = ((mainTextLineStart.y + mainTextLineEnd.y) / 2);
                            if (horizontalSubPointX > mainTextLineStart.x && horizontalSubPointX < mainTextLineEnd.x) {
                                // and y-distance is less than 1.5 interline
                                if (Math.abs(averageSubPointY - mainTextLineY) < maxDistance) {
                                    cluster.add(subTextLine);
                                    removedLines.add(subTextLine);
                                    newLinesAdded = true;
                                }
                            }
                        }
                    }
                    if (newLinesAdded) {
                        break;
                    }
                }
            }

//            List<TextLine> finalTextlines = new ArrayList<>();


            TextRegion textRegion = new TextRegion();
            textRegion.setId(UUID.randomUUID().toString());
            Coords coords = new Coords();
            textRegion.setCustom("structure {type:Text;}");
            Rect regionPoints = LayoutProc.getBoundingBoxTextLines(cluster);
            coords.setPoints(StringConverter.pointToString(regionPoints));
            textRegion.setCoords(coords);

            cluster.sort(Comparator.comparing(textLine1 ->
                    StringConverter.distance(new Point(regionPoints.x, regionPoints.y),
                            new Point(regionPoints.x + (StringConverter.stringToPoint(textLine1.getBaseline().getPoints()).get(0).x - regionPoints.x) / 10, StringConverter.stringToPoint(textLine1.getBaseline().getPoints()).get(0).y))));
            int counter = 0;
            for (TextLine textLine1 : cluster) {
                textLine1.setCustom("readingOrder {index:" + counter + ";}");
                counter++;
            }
            textRegion.setTextLines(cluster);


            page.getPage().getTextRegions().add(textRegion);
        }

        LayoutProc.reorderRegions(page);

        TextRegion lastRegion = null;
        for (TextRegion textRegion : page.getPage().getTextRegions()) {
            if (lastRegion == null
                    && (textRegion.getCustom().contains(":Text")
                    || textRegion.getCustom().contains(":ParHeader"))
            ) {
                lastRegion = textRegion;
                continue;
            } else if (lastRegion == null) {
                continue;
            }
            Rect lastBoundingBox = LayoutProc.getBoundingBoxTextLines(lastRegion.getTextLines());
            Rect boundingBox = LayoutProc.getBoundingBoxTextLines(textRegion.getTextLines());
            if (lastRegion.getTextLines().size() == 1
                    && lastBoundingBox.y < boundingBox.y  // last above current
                    && lastBoundingBox.x < boundingBox.x + boundingBox.width  //position directly above
                    && lastBoundingBox.x + lastBoundingBox.width > boundingBox.x  //position directly above
                    && lastBoundingBox.y + lastBoundingBox.height + (3 * interlinemedian) > boundingBox.y
            ) {
                lastRegion.setCustom("structure {type:ParHeader;}");
            }
            lastRegion = textRegion;
        }


        page.getMetadata().setLastChange(new Date());

        if (page.getMetadata().getMetadataItems() == null) {
            page.getMetadata().setMetadataItems(new ArrayList<>());
        }
        MetadataItem metadataItem = new MetadataItem();
        metadataItem.setType("processingStep");
        metadataItem.setName("reading-order");
        metadataItem.setValue("loghi-htr-tooling");

        page.getMetadata().getMetadataItems().add(metadataItem);

        return page;
    }

    @Override
    public void run() {
        try {
            PcGts newPage = runPage(identifier, page, cleanBorders, borderMargin, asSingleRegion);
            pageSaver.accept(newPage);
        } finally {
            try {
                this.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
