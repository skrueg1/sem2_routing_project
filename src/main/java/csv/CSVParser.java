package csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// Iterable allows us to use a foreach loop
// and AutoCloseable allows to use in a try catch block
// these interfaces are only implemented because that's
// what the old library was doing, so all we have to do is just
// switch the classes
public class CSVParser implements AutoCloseable, Iterable<CSVRecord> {
    
    private List<CSVRecord> records = new ArrayList<>();
    
    public CSVParser(BufferedReader reader) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) return;

        // use proper parsing instead of split
        String[] headers = parseLine(headerLine);

        String line;
        while ((line = reader.readLine()) != null) {
            String[] values = parseLine(line);

            // normalize row length to match headers
            String[] normalized = new String[headers.length];
            for (int i = 0; i < headers.length; i++) {
                if (i < values.length) {
                    normalized[i] = clean(values[i]);
                } else {
                    normalized[i] = null;
                }
            }

            records.add(new CSVRecord(headers, normalized));
        }
    }
    
    @Override
    public void close() {
    }

    @Override
    public Iterator<CSVRecord> iterator() {
        return records.iterator();
    }
    
    private String[] parseLine(String line) {
        
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        // this goes through each character in the line and handles quotes and commas properly
        // bcs CSV can have entries like "Netherlands, Amsterdam" that shouldnt be separated by the comma 
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // quotes
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++; // skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            // only separate on commas if we are not in a quote
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
       
        // String[]::new is a method ref so we dont need to create a new array
        return values.toArray(String[]::new);
    }

    private String clean(String value) {
        if (value == null) return null;
        
        // trim spaces
        value = value.trim();

        // remove quotes around the value
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }

        return value;
    }
}