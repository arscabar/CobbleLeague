package ai.league.recorder;

import java.io.IOException;
import java.util.List;

final class SheetXml {
    static String create(List<List<?>> rows, int header, double[] widths) throws IOException {
        if (rows.size() > 1_048_576 || widths.length > 16_384) throw new IOException("Excel table size exceeded");
        StringBuilder xml = new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"").append(header)
                .append("\" topLeftCell=\"A").append(header + 1).append("\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>");
        for (int i = 0; i < widths.length; i++) xml.append("<col min=\"").append(i + 1).append("\" max=\"")
                .append(i + 1).append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
        xml.append("</cols><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            xml.append("<row r=\"").append(r + 1).append("\">");
            List<?> cells = rows.get(r);
            int style = XlsxStyles.row(cells, r, header);
            for (int c = 0; c < cells.size(); c++) {
                Object value = cells.get(c);
                if (value == null && style == 0) continue;
                xml.append("<c r=\"").append(column(c)).append(r + 1).append("\" s=\"")
                        .append(style).append('"');
                if (value == null) { xml.append("/>"); continue; }
                if (value instanceof Number n) {
                    if (!Double.isFinite(n.doubleValue())) throw new IOException("Non-finite Excel value");
                    xml.append("><v>").append(n).append("</v></c>");
                } else {
                    String text = value.toString();
                    if (text.length() > 32_767) throw new IOException("Excel cell text exceeds 32767 characters");
                    xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escape(text)).append("</t></is></c>");
                }
            }
            xml.append("</row>");
        }
        return xml.append("</sheetData><autoFilter ref=\"A").append(header).append(':')
                .append(column(widths.length - 1)).append(rows.size()).append("\"/></worksheet>").toString();
    }

    private static String column(int index) {
        StringBuilder s = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) s.append((char) ('A' + (n - 1) % 26));
        return s.reverse().toString();
    }

    static String escape(String text) {
        StringBuilder s = new StringBuilder();
        text.codePoints().filter(c -> c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xD7FF
                || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF).forEach(s::appendCodePoint);
        return s.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
