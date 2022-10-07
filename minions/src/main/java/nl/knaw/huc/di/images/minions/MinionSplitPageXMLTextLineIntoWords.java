package nl.knaw.huc.di.images.minions;

import nl.knaw.huc.di.images.layoutanalyzer.layoutlib.LayoutProc;
import nl.knaw.huc.di.images.layoutds.models.Page.PcGts;
import nl.knaw.huc.di.images.pagexmlutils.PageUtils;
import nl.knaw.huc.di.images.stringtools.StringTools;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MinionSplitPageXMLTextLineIntoWords {
    public static void main(String[] args) throws Exception {
        String input = "/scratch/limited/page";
        if (args.length > 0) {
            input = args[0];
        }
        Path inputPath = Paths.get(input);
        DirectoryStream<Path> fileStream = Files.newDirectoryStream(inputPath);
        List<Path> files = new ArrayList<>();
        fileStream.forEach(files::add);
        files.sort(Comparator.comparing(Path::toString));

        for (Path file : files) {
            if (file.getFileName().toString().endsWith(".xml")) {
                String pageXml = StringTools.readFile(file);
                PcGts page = PageUtils.readPageFromString(pageXml);
                LayoutProc.splitLinesIntoWords(page);
                String newPageXml = PageUtils.convertPcGtsToString(page);
                StringTools.writeFile(file.toAbsolutePath().toString(), newPageXml);
            }

        }
    }
}
