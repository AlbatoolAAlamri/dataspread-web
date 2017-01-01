import org.zkoss.zss.model.CellRegion;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.*;


public class DepGraphOpt {
    // Dependency graph
    DependencyGraph originalGraph;
    private int candidatesGenerated;
    final boolean pruning = true;


    private int graphsExplored; // This includes the sub graphs explored
    private double FPwithinBudget;
    private int memoryBudget;

    DepGraphOpt(DependencyGraph originalGraph) {
        this.originalGraph = originalGraph;
        FPwithinBudget = 1.0;

    }

    public int getGraphsExplored() {
        return graphsExplored;
    }

    DependencyGraph getOptimalGraph(int memoryBudget) {
        this.memoryBudget = memoryBudget;
        Stream<DependencyGraph> dependencyGraphStream = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        getAllCandidates(DependencyGraph.Side.DEPENDSON), Spliterator.DISTINCT), false);

        // dependencyGraphStream.forEach(e-> System.out.println(" Candidate:- " + e) );


        return dependencyGraphStream
                .filter(e -> e.size() <= memoryBudget)
                .filter(e -> {
                    FPwithinBudget = Math.min(FPRate(e), FPwithinBudget);
                    return FPRate(e) <= FPwithinBudget;
                })
                .min(Comparator.comparingDouble(e -> FPRate(e))).orElse(null);

    }



    public int getCandidatesGenerated() {
        return candidatesGenerated;
    }

    public Iterator<DependencyGraph> getAllCandidates(DependencyGraph.Side side) {
        candidatesGenerated = 0;
        graphsExplored = 0;
        return getAllCandidates(originalGraph, side, true);
    }


    public Iterator<DependencyGraph> getAllCandidates(DependencyGraph inputGraph,
                                                      DependencyGraph.Side side,
                                                      boolean outerCall) {
        Iterator<DependencyGraph> dependencyGraphIterator = new Iterator<DependencyGraph>() {
            // Pull the next sub solution
            boolean pullNextSubSolution;
            // Index within the sub solution
            int subIndex;

            DependencyGraph subSolution;
            List<CellRegion> subSolutionDependsOnList;

            // This is the graph with one node removed
            DependencyGraph partial;

            // Removed nodes.
            CellRegion removedDependsOn = null;
            Set<CellRegion> removedDependsSet = null;


            // Next solution to return.
            DependencyGraph nextSolution;

            // Sub solutions.
            Iterator<DependencyGraph> subSet = null;

            {
                //Remove first node
                partial = inputGraph.copy();
                removedDependsOn = partial.getDependsOnSet().stream().findAny().orElse(null);

                if (removedDependsOn != null) {
                    removedDependsSet = partial.deleteDependsOn(removedDependsOn);
                    subSet = getAllCandidates(partial, side, false);
                    pullNextSubSolution = true;
                    nextSolution = getNextCandidate();
                }
                else {
                    // Base case, empty graph.
                    nextSolution = inputGraph;
                }

            }


            private DependencyGraph getNextCandidate() {
                // Base case
                if (subSet == null)
                    return null;


                DependencyGraph nextCandidate = null;
                while (nextCandidate == null) {
                    if (pullNextSubSolution) {
                        if (subSet.hasNext()) {
                            subSolution = subSet.next();

                            //TODO: Get a list based on distance from removedDependsOn
                            subSolutionDependsOnList = subSolution.getDependsOnSet()
                                    .stream()
                                    //The order here is important as it can reduce the number of candidates.
                                    //TODO: think of a better way  to organizes
                                    .sorted(Comparator.comparing(e -> e.getRow()))
                                    .collect(Collectors.toCollection(ArrayList::new));

                            subIndex = -1; // First candidate, where regions are not combined.
                            pullNextSubSolution = false;
                        } else {
                            // All done
                            break;
                        }
                    }

                    DependencyGraph solution = subSolution.copy();
                    for (CellRegion depends : removedDependsSet)
                        solution.put(depends, removedDependsOn);
                    if (subIndex >= 0)
                        solution.mergeTwoDependsOn(removedDependsOn, subSolutionDependsOnList.get(subIndex));
                    //TODO: before mearging check its impact on the FP rate.
                    // Maintin a strucutre that stores bad combinations.


                    subIndex++;

                    if (subIndex == subSolutionDependsOnList.size())
                        pullNextSubSolution = true;


                    if (isCandidateGood(solution))
                        nextCandidate = solution;
                }
                return nextCandidate;
            }

            private boolean isCandidateGood(DependencyGraph solution) {
                if (!pruning)
                    return true;
                // Do not consider sub-solutions that violate memory budget
                if (solution.size() > memoryBudget)
                    return false;

                // Use the anti-monotonicity property.
                // Need to check if the pruning is correct.
                // This FP rate is for the sub solution, while comparing it with the original graph.
                // FPwithinBudget - The best complete solution seen till now.
                if (FPRate(solution) > FPwithinBudget)
                    return false;

                return true;
            }

            @Override
            public boolean hasNext() {
                return nextSolution == null ? false : true;
            }

            @Override
            public DependencyGraph next() {
                //  Number of candidate fetched in the final call
                if (outerCall)
                    ++candidatesGenerated;
                ++graphsExplored;

                // Get the next solution
                DependencyGraph solutionToReturn = nextSolution;
                nextSolution = getNextCandidate();
                return solutionToReturn;

            }
        };

        return dependencyGraphIterator;
    }


    public double FPRate(DependencyGraph newGraph)
    {
        return (double) (newGraph.area() - originalGraph.area()) / newGraph.area();
    }

    public DependencyGraph greedyMerge(int memoryBudget) {
        // Greedily merge two areas the have the least impact on FP rate.
        DependencyGraph current = originalGraph.copy();
        while (current.size() > memoryBudget) {
            DependencyGraph bestMerged = null;
            double bestFPRate = 1.0;

            // Try merging dependsOn
            List<CellRegion> dependsOnList = current.getDependsOnSet()
                    .stream()
                    .collect(Collectors.toCollection(ArrayList::new));

            for (int i = 0; i < dependsOnList.size() - 1; ++i) {
                for (int j = i + 1; j < dependsOnList.size(); ++j) {
                    DependencyGraph reducedGraph = current.copy();
                    reducedGraph.mergeTwoDependsOn(dependsOnList.get(i), dependsOnList.get(j));
                    if (FPRate(reducedGraph) < bestFPRate) {
                        bestMerged = reducedGraph;
                        bestFPRate = FPRate(reducedGraph);
                    }
                }
            }

            // Try merging depends
            List<CellRegion> dependsList = current.getDependsSet()
                    .stream()
                    .collect(Collectors.toCollection(ArrayList::new));

            for (int i = 0; i < dependsList.size() - 1; ++i) {
                for (int j = i + 1; j < dependsList.size(); ++j) {
                    DependencyGraph reducedGraph = current.copy();
                    reducedGraph.mergeTwoDepends(dependsList.get(i), dependsList.get(j));
                    if (FPRate(reducedGraph) < bestFPRate) {
                        bestMerged = reducedGraph;
                        bestFPRate = FPRate(reducedGraph);
                    }
                }
            }

            current = bestMerged;
        }
        return current;
    }


    public static void main(String[] args) throws IOException {
        DependencyGraph originalGraph;
        originalGraph = new DependencyGraph();

        FileInputStream inputStream = new FileInputStream("TestCode/SampleGraph.txt");

        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String s;
        while ((s = br.readLine()) != null) {
            if (s.startsWith("#"))
                continue;
            String formula[] = s.split("=");
            String tokens[] = formula[1].split("[ \t*+-/()<>!,]");
            for (String token : tokens)
                if (token.matches("[A-Z]+[0-9]+:[A-Z]+[0-9]+") || token.matches("[A-Z]+[0-9]+"))
                    originalGraph.put(new CellRegion(formula[0]),
                            new CellRegion(token));

        }
        System.out.println("Original Graph ");
        System.out.print(originalGraph);
        System.out.println();


        int memoryBudget = 5;
        DepGraphOpt depGraphOpt = new DepGraphOpt(originalGraph);
        DependencyGraph sol = depGraphOpt.getOptimalGraph(memoryBudget);

        if (sol != null) {
            System.out.println("DP Solution");
            System.out.println("Candidates " + depGraphOpt.getCandidatesGenerated());
            System.out.println("Graphs Explored  " + depGraphOpt.getGraphsExplored());


            System.out.println("FP Rate " + depGraphOpt.FPRate(sol));
            System.out.println(sol);
        }

        DependencyGraph greedySol = depGraphOpt.greedyMerge(memoryBudget);
        System.out.println("Greedy Solution");
        System.out.println("FP Rate " + depGraphOpt.FPRate(greedySol));
        System.out.println(greedySol);

        System.out.println(greedySol.getMergeOperations());



    }


}