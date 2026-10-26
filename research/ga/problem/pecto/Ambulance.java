package problem.pecto;

import java.util.ArrayList;
import java.util.List;

public class Ambulance implements Participant{
    private final int index;
    private int currentLocation;
    private final List<Participant> route;

    public Ambulance(int index) {
        this.index = index;
        this.currentLocation = index;
        this.route = new ArrayList<>();
        route.add(this);
    }

    public void setCurrentLocation(int newLocation) {
        this.currentLocation = newLocation;
    }

    public List<Participant> getRoute() {
        return route;
    }

    public void addRoute(Participant participant) {
        route.add(participant);
    }

    public void updateRoute(int index, Participant participant) {
        route.set(index, participant);
    }

    public void insertRoute(int index, Participant participant) {
        route.add(index, participant);
    }

    public void removeFromRoute(int index) {
        route.remove(index);
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public int getLocation() {
        return currentLocation;
    }
}
