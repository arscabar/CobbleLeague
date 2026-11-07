package ai.league.recorder;

import java.util.List;

final class XlsxStyles {
    static final String XML = xml();

    static int row(List<?> cells, int index, int header) {
        if (index + 1 == header || header > 1 && index == 0) return 1;
        if (header != 1 || cells.size() < 6) return 0;
        String action = String.valueOf(cells.get(3)), result = String.valueOf(cells.get(5));
        if (result.contains("급소")) return 2;
        if (action.equals("교체") || action.equals("위치 교환")) return 5;
        if (action.equals("기절") || result.contains("기절")) return 3;
        if (action.equals("회복")) return 6;
        if (action.startsWith("상태이상") || action.matches("(?:맹독|독|화상) 피해")
                || result.matches(".*(?:맹독|독|화상|마비|잠듦|얼음) 부여.*")) return 4;
        return 7;
    }

    private static String xml() {
        String[] fonts = {"FF000000", "FFFFFFFF", "FF9C0006", "FF404040", "FF7030A0", "FF1F4E78", "FF006100"};
        String[] fills = {"FF16324F", "FFFFE3E3", "FFE7E6E6", "FFF1E4F7", "FFE4EFFA", "FFE2F0D9"};
        StringBuilder s = new StringBuilder("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"7\">");
        for (int i = 0; i < fonts.length; i++) {
            s.append("<font><sz val=\"11\"/><name val=\"Calibri\"/><color rgb=\"").append(fonts[i]).append("\"/>");
            if (i >= 1 && i <= 3) s.append("<b/>");
            s.append("</font>");
        }
        s.append("</fonts><fills count=\"8\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>");
        for (String color : fills) s.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"")
                .append(color).append("\"/></patternFill></fill>");
        s.append("</fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf/></cellStyleXfs><cellXfs count=\"8\"><xf fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>");
        for (int i = 1; i <= 6; i++) s.append("<xf fontId=\"").append(i).append("\" fillId=\"").append(i + 1)
                .append("\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>");
        return s.append("<xf fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"><alignment vertical=\"top\" wrapText=\"1\"/></xf></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>").toString();
    }
}
