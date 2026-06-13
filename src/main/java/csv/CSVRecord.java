package csv;

public class CSVRecord {
    private String[] headers;
    private String[] values;
    
    public CSVRecord(String[] headers, String[] values) {
        this.headers = headers;
        this.values = values;
    }
    
    public String get(String header) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equals(header)) {
                return values[i];
            }
        }
        return null;
    }
}
