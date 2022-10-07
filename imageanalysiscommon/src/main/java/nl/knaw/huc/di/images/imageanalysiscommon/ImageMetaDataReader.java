package nl.knaw.huc.di.images.imageanalysiscommon;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import java.io.File;
import java.io.IOException;

public class ImageMetaDataReader {


    public static void main(String[] args) throws ImageProcessingException, IOException {
        readMetaData();
    }

    public static void readMetaData() throws ImageProcessingException, IOException {
        Metadata metadata = ImageMetadataReader.readMetadata(new File("/home/rutger/data/transkribus/13131/13131/A31239000945.jpg"));

        String createDate = null;
        String lat = null;
        String lon = null;
        for (Directory directory : metadata.getDirectories()) {
            for (Tag tag : directory.getTags()) {
                String tagName = tag.getTagName();
                String desc = tag.getDescription();
                System.out.println(tagName);
                System.out.println(desc);
                System.out.println();
                switch (tagName) {
                    case "Date/Time Original":
                        createDate = desc.split(" ")[0].replace(":", "-");
                        break;
                    case "GPS Latitude":
                        lat = desc;
                        break;
                    case "GPS Longitude":
                        lon = desc;
                        break;
                }
            }
        }
    }
}
