package simulation;

import engine.Point;

public class TestTrip {
    private int tripID;
    private Point startingPoint;
    private Point endingPoint;

    public TestTrip(int tripID, Point start, Point end) {
        this.tripID = tripID;
        this.startingPoint = start;
        this.endingPoint = end;
    }

    public Point getStartingPoint() {
        return startingPoint;
    }

    public Point getEndingPoint() {
        return endingPoint;
    }

    public int getID() {
        return tripID;
    }
}
