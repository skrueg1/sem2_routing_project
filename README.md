# Team_10


## Routing engine
This is the repository for Project 1.2, a routing engine.

## Run the code
First, compile the project:
```
mvn compile
```

for the GUI, run:
```
mvn javafx:run
```

for the command line only version, run the project starting from the RoutingEngine's main function
```
mvn exec:java -Dexec.mainClass="RoutingEngine"
```

`Starting` Should be printed to the console, after which you may input a command in a JSON format. 

You always have to use the `load` command before issuing other commands.

## Commands

| Command | Description | Parameters | Success Response| Error Response |
|-|-|-|-|-|
|`load`| Loads a GTFS dataset from a ZIP file and resets previous data. | `filename` (string):Local path to a GTFS ZIP file | `{"ok":"loaded"}`| `{"error": errorString}`|
| `routeFrom` | Calculates a simple walking route.| `sourcePoint`, `to`, `startingAt` (time string)| `{"ok":[routeStep]}`| `{"error": errorString}`|

### Example usage
```
> {"load": "data/GTFS.zip"}

< {"ok":"loaded"}
```

```
> {"routeFrom":{"lat":55.67502530944051,"lon":12.567332097325794},"to":{"lat":55.6742134771295,"lon":12.569346855273748},"startingAt":"08:30"}

< {"ok":[{"duration":2,"mode":"walk","startTime":"08:30","to":{"lat":55.6742134771295,"lon":12.569346855273748}}]}
```

## Simulation: route closure impact

Generate the test trips:
```
mvn compile
java -cp target/classes simulation.TestTripGenerator
```

Calculate the baseline average travel time, then close each route/stop one at a time and rank the route/stop closures:
```
mvn compile
java -cp target/classes simulation.ClosureImpactAnalyzer
java -cp target/classes simulation.ClosureImpactAnalyzer --rank-stops=true  
```

The route closure results are written to:
```
data/Simulations/route_closure_results.csv
```
The stop closure results are written to:
```
data/Simulations/stop_closure_results.csv
```

Useful options:
```
--trips=data/Simulations/test_trips.csv
--gtfs=data/copenhagen_inner_gtfs.zip
--route-output=data/Simulations/route_closure_results.csv
--start=08:30
--day=0
--top=15
--trip-limit=100
--close-route=7466_3
--include-unused-routes=true
```

`--day=0` means Monday. By default, the analyzer only tests routes used by at least one baseline trip, because closing an unused route does not change these generated trips.
