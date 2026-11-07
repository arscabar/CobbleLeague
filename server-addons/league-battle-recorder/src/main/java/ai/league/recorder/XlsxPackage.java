package ai.league.recorder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

final class XlsxPackage {
    private static final String REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String DOC = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
    private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    static void write(ZipOutputStream zip, String party, String events) throws IOException {
        put(zip, "[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + type("/xl/workbook.xml", "sheet.main") + type("/xl/styles.xml", "styles")
                + type("/xl/worksheets/sheet1.xml", "worksheet") + type("/xl/worksheets/sheet2.xml", "worksheet") + "</Types>");
        put(zip, "_rels/.rels", "<Relationships xmlns=\"" + REL + "\">" + link("rId1", "officeDocument", "xl/workbook.xml") + "</Relationships>");
        put(zip, "xl/workbook.xml", "<workbook xmlns=\"" + MAIN + "\" xmlns:r=\"" + DOC.substring(0, DOC.length() - 1)
                + "\"><sheets><sheet name=\"파티\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"전투기록\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
        put(zip, "xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"" + REL + "\">"
                + link("rId1", "worksheet", "worksheets/sheet1.xml") + link("rId2", "worksheet", "worksheets/sheet2.xml")
                + link("rId3", "styles", "styles.xml") + "</Relationships>");
        put(zip, "xl/styles.xml", XlsxStyles.XML);
        put(zip, "xl/worksheets/sheet1.xml", party);
        put(zip, "xl/worksheets/sheet2.xml", events);
    }

    private static String type(String path, String kind) {
        return "<Override PartName=\"" + path + "\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml." + kind + "+xml\"/>";
    }
    private static String link(String id, String kind, String target) {
        return "<Relationship Id=\"" + id + "\" Type=\"" + DOC + kind + "\" Target=\"" + target + "\"/>";
    }
    private static void put(ZipOutputStream zip, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
