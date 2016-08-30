import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/*
Project Euler: Problem 502 - https://projecteuler.net/problem=502
 
"A block is defined as a rectangle with a height of 1 and an integer-valued length.
Let a castle be a configuration of stacked blocks.

Given a game grid that is w units wide and h units tall, a castle is generated according to the following rules:

1 Blocks can be placed on top of other blocks as long as nothing sticks out past the edges or hangs out over open space.
2 All blocks are aligned/snapped to the grid.
3 Any two neighboring blocks on the same row have at least one unit of space between them.
4 The bottom row is occupied by a block of length w.
5 The maximum achieved height of the entire castle is exactly h.
6 The castle is made from an even number of blocks.

Let F(w, h) represent the number of valid castles, given grid parameters w and h.
For example, F(4, 2) = 10, F(13, 10) = 3729050610636, F(10, 13) = 37959702514, and F(100, 100) mod 1 000 000 007 =
841913936."

Summary:
The algorithm calculates the number of castles on a w*h grid which satisfy the above conditions.
The algorithm must be fast enough to calculate (F(1012, 100) + F(10000, 10000) + F(100, 1012)) mod 1 000 000 007.

Lengthy explanation of approach and rationale:
This problem deals with finding permutations on a w*h grid that meet a precise set of criteria (see 1-6 above). My
proposed solution is wholly inadequate for finding (F(1012, 100) + F(10000, 10000) + F(100, 1012)) mod 1 000 000 007 -
to my mind, no optimization would be enough. Due to the exponential nature of the castle's state space, I suspect
that an equation is required. Thanks to the BigInteger library, this solution could even be recursive (à la Fibonnaci
Sequence). However, all of my attempts to derive an equation thus far for widths / heights beyond 3 have failed.
This program is thus an exploration of the state space in hopes of obtaining a better understanding.

The basic idea is to start from a blank slate (dimensions: 3 x 3; X := block, - := unavailable open space):


XXX

and take each option:
      - -    -  - -  -
XXX XX- -XX X-  -X-  -X
XXX XXX XXX XXX XXX XXX

We maintain an ArrayList of spaces for both the current and next row, allowing constant-time building / removal
spot location. After every move, we check to see if the current configuration satisfies the 6 criteria listed above.
If it does, we add it to the sum tracked for the current iteration of the function, which is eventually returned and
added to those found by the other branches pursued in this depth-first search of the castle space.

The requisite functions are provided by the Castle class.

TODO: Split experimental changes into Git branch
TODO: Find a way to visualize per-block number solution distributions
TODO: Implement ResultTRIE
TODO: Complete wrapper function for memoiseCastle
TODO: Know which castles must be pre-calculated and add them in the correct order, Fibonacci-style
 */
public class fivehundredtwo {
    private static Castle globalCastle = new Castle(4, 13);

    /**
     *  cachedMovesRec[i] contains the moves available for a free space of size i that weren't available
     *  at the smaller size spaces.
     *
     *  Move m(column, width)
     *  e.g. at cachedMoves.get(1): (0,1), (1,1), (2,1), (3,1) for w = 4
     */
    private static List<ArrayList<Move>> cachedMovesRec = new ArrayList<>(globalCastle.width+1);

    // IN PROGRESS: castleResults[x][y] stores even and odd solutions for castles of dimensions x by y (counting the base)
    private static Result[][] castleResults = new Result[globalCastle.width + 1][globalCastle.height + 1];
    // For a given width and height, how do the solutions break down over the number of blocks used?
    private static int[][][] blockNumberResults = new int[globalCastle.width + 1][globalCastle.height + 1]
            [globalCastle.width*globalCastle.height];

    public static void main(String[] args) {
        prepCachedMovesRec();
        iterateCastles(globalCastle.width, globalCastle.height);
    }

    /**
     * Cache the moves for the current width of globalCastle.
     */
    private static void prepCachedMovesRec(){
        cachedMovesRec = new ArrayList<>(globalCastle.width + 1);
        cachedMovesRec.add(new ArrayList<>());

        for(int size = 1; size <= globalCastle.width; size++){
            // initialize ArrayList of moves
            cachedMovesRec.add(new ArrayList<>());
            for(int i = 0; i <= globalCastle.width-size; i++)
                cachedMovesRec.get(size).add(new Move(i, size));
        }
    }

    private static void iterateCastles(int widthBound, int heightBound) {
        System.out.println("Iterating over castle sizes (dimensions not exceeding "
                + widthBound + " by " + heightBound + ")");

        // Count castles from 1 to widthBound, 1 to heightBound
        for(int i = 1; i <= widthBound; i++){
            for(int j = 1; j <= heightBound; j++){
                if(j == 1){
                    castleResults[i][j] = new Result(0, 1);
                } else if(i == 1){ // special case where we can predetermine results
                    castleResults[i][j] = new Result((j + 1) % 2, j % 2);
                } else {
                    globalCastle = new Castle(i,j);
                    castleResults[i][j] = enumerateCastleRec(0);
                }
                castleResults[i][j].display();
                if(j < heightBound)
                    System.out.print("; ");
            }
            System.out.println();
        }
    }

    /**
     * Recursively enumerates castles on globalCastle.
     *
     * @param spaceIndex the space currently being operated in is in the spaceIndex-nth position of globalCastle's
     *                   spaces ArrayList.
     * @precondition globalCastle exists.
     * @precondition prepCachedMovesRec has been run.
     * @return sum a Result containing the number of even- and odd-block-numbered castles matching the given criteria
     */
    private static Result enumerateCastleRec(int spaceIndex){
        Result sum = new Result();

        /* Normal code:
        if(globalCastle.isEvenSolution())
            sum.incrementEven();
         */
        if(globalCastle.areBlocksInLastRow()){
            // Mark how solutions are distributed across number of blocks used
            blockNumberResults[globalCastle.width][globalCastle.height][globalCastle.getLastID()]++;
            if(globalCastle.lastIDEven())
                sum.incrementEven();
            else
                sum.incrementOdd();
        }

        if(globalCastle.canAddBlock()) {
            int lastSpaceIndex;

            for(; spaceIndex < globalCastle.spacesInRow[globalCastle.current]; spaceIndex++){
                Space s = globalCastle.spaces.get(globalCastle.current).get(spaceIndex);
                int spaceSize = s.getWidth();
                // Execute the possible moves
                for(int first = 1; first <= spaceSize; first++)
                    for(int second = 0; second <= spaceSize - first; second++) {
                        Move m = cachedMovesRec.get(first).get(second),
                                // increment by current index; cachedMovesRec doesn't account for offset from current block
                                nextMove = new Move(m.getIndex() + s.getIndex(), m.getWidth());

                        lastSpaceIndex = globalCastle.placeBlockUpdate(nextMove, spaceIndex);
                        if(globalCastle.skipSpace){
                            globalCastle.skipSpace = false;
                            sum.addResult(enumerateCastleRec(lastSpaceIndex+1));
                        } else {
                            sum.addResult(enumerateCastleRec(lastSpaceIndex));
                        }
                        globalCastle.removeBlockUpdate(nextMove, lastSpaceIndex);
                    }}}

        if (globalCastle.canAdvance()){
            globalCastle.advanceRow();
            sum.addResult(enumerateCastleRec(0));
            globalCastle.retreatRow();
        }

        return sum;
    }


    /**
     * IN PROGRESS: wrapper for the memoiseCastle function
     */
    private static Result memoiseCastleWrapper(int w, int h){
        // we can calculate this kind of castle by formula
        if(w == 1)
            return new Result((h + 1) % 2, h % 2);
        // set the globals

        // create the castle - add check
        globalCastle = new Castle(w, h);
        // pre-generate available moves - ADD check for whether already done
        prepCachedMovesRec();

        return memoiseCastle(0);
    }

    /**
     * Store results for each castle size and use that information to calculate how many combinations are possible from
     *  a given configuration.
     *
     * @param spaceIndex the space currently being operated in is in the spaceIndex-nth position of globalCastle's
     *                   spaces ArrayList.
     *
     * @return sum a Result
     *
     * @theory
     * In principle, we can memoise results; however, it would entail an enormous space complexity. Let's say we have
     * two castles:
     *    -   -
     * XXX-   -XXX
     * XXXXX XXXXX
     *
     * You would imagine that they lead to the same number of solutions - and you'd be right! However, this isn't just
     * because they share the same spaces list for the current row; it's because the next row also contains a 3-wide
     * space for both castles. So not only do you have to hash the stored results by the dimensions of the part of the
     * castle that is under consideration (treating the base as part of the height: [2 (w)][5 (h)]):
     * A:   B:
     *   ---  ---
     *   ---  ---
     *   ---  ---
     *   --- X---
     * XX--- XX--X
     * XXXXX XXXXX
     *
     * but also by each of the spaces (categorized by their width and the space above them). The spaces should be put
     * into descending order by their width and then by their height. For A, the storage would be routed to
     * memoisedResults[2][5]; for B, to memoisedResults[1][5][1][3]. All spaces in the current castle must be taken
     * into consideration to accurately store and retrieve memoised data.
     *
     * Further specificity is provided in the ResultTRIE documentation.
     */
    private static Result memoiseCastle(int spaceIndex){
        Result sum = new Result();
        if(globalCastle.areBlocksInLastRow()){
            if(globalCastle.lastIDEven())
                sum.incrementEven();
            else
                sum.incrementOdd();
        }

        if (globalCastle.canAdvance()){
            globalCastle.advanceRow();
            sum.addResult(memoiseCastle(0));
            globalCastle.retreatRow();
        }

        if(globalCastle.canAddBlock()) {
            int lastSpaceIndex;

            for(; spaceIndex < globalCastle.spacesInRow[globalCastle.current]; spaceIndex++){
                Space s = globalCastle.spaces.get(globalCastle.current).get(spaceIndex);

                int spaceSize = s.getWidth();
                // go down a row to include the square and compensate for indexing by 0
                int projectedHeight = globalCastle.current + 2;

                // check to see if we already know the results
                // Usage seems to be correct when it's called, so perhaps it has to do with the flow
                if(castleResults[spaceSize][projectedHeight] != null) {
                    //globalCastle.display(false);
                    if(globalCastle.lastIDEven())
                        sum.addResult(castleResults[spaceSize][projectedHeight].flip());
                    else
                        sum.addResult(castleResults[spaceSize][projectedHeight]);
                } else {
                    // Execute the possible moves
                    for(int first = 1; first <= spaceSize; first++) {
                        for(int second = 0; second <= spaceSize - first; second++) {
                            Move m = cachedMovesRec.get(first).get(second),
                                    /* increment by current index; cachedMovesRec doesn't account for offset from
                                    current block */
                                    nextMove = new Move(m.getIndex() + s.getIndex(), m.getWidth());
                            lastSpaceIndex = globalCastle.placeBlockUpdate(nextMove, spaceIndex);
                            if(globalCastle.skipSpace){
                                globalCastle.skipSpace = false;
                                sum.addResult(memoiseCastle(lastSpaceIndex+1));
                            } else {
                                sum.addResult(memoiseCastle(lastSpaceIndex));
                            }
                            globalCastle.removeBlockUpdate(nextMove, lastSpaceIndex);
                        }
                    }
                }}}

        return sum;
    }
}

/**
 * A robust interface for generating and manipulating Castles.
 */
class Castle{
    int height, width;
    int current; // the current row index
    private int lastID; // the ID of the last block placed
    private int placedInRow[]; // how many blocks have been placed in each row
    int spacesInRow[]; // how many spaces are in each row
    boolean skipSpace; // global flag for knowing when to move to next space
    private boolean lastIDEven; // track whether the ID of the last block placed is even
    private boolean unavailableColumn[]; // Tracks columns that aren't available - saves up to height operations / call
    List<ArrayList<Space>> spaces; // track available spaces for each level - update while placing / removing blocks
    private boolean[][] blocks; // True if a block exists there.

    /**
     * Construct a Castle with the bottom row filled in.
     *
     * @param w the width of the Castle
     * @param h the height of the Castle
     * @precondition w > 0
     * @precondition h > 0
     */
    Castle(int w, int h){
        // Initialization
        this.width = w;
        this.unavailableColumn = new boolean[this.width];
        this.height = h;
        this.current = this.height - 1;
        this.lastIDEven = true;
        this.lastID = 0;
        this.placedInRow = new int[this.height];
        this.spacesInRow = new int[this.height];
        this.spaces = new ArrayList<>(this.height);
        for(int i = 0; i < this.height; i++)
            this.spaces.add(new ArrayList<>());
        this.skipSpace = false;
        this.blocks = new boolean[this.height][this.width];

        // Place first space and block
        this.spaces.get(this.current).add(new Space(0, this.width));
        this.spacesInRow[this.current]++;
        placeBlockUpdate(new Move(0, this.width), 0);

        // Leave the first row
        this.current--;
    }

    /**
     * Robust block placement using constant-time space-navigation logic.
     *
     * @param m a Move describing the new block to be placed
     * @param spaceIndex the space currently being operated in is in the spaceIndex-nth position of globalCastle's
     *                   spaces ArrayList.
     * @precondition m and spaceIndex describe a valid Move in a valid space
     * @return newIndex index of left space created by the displacement m causes in the space. If no spaces remain,
     * -1 is returned.
     */
    int placeBlockUpdate(Move m, int spaceIndex){
        int leftSide = m.getIndex() - 1, rightSide = m.getIndex() + m.getWidth(), newIndex;

        // Lay the block
        this.lastIDEven = !this.lastIDEven;
        this.lastID++;

        for(int i = m.getIndex(); i < rightSide; i++)
            this.blocks[this.current][i] = true;

        // mark sides as unavailable
        if(leftSide >= 0)
            this.unavailableColumn[leftSide] = true;
        if(rightSide < this.width)
            this.unavailableColumn[rightSide] = true;
        this.placedInRow[this.current]++;

        // Add new space above
        modifySpaceAbove(m, true);

        Space s = this.spaces.get(this.current).get(spaceIndex);
        this.spaces.get(this.current).remove(spaceIndex);
        this.spacesInRow[this.current]--;

        boolean modifyLeft = leftSide > s.getIndex(),
                // if space ends past the right bound of the move
                modifyRight = rightSide+1 < s.getIndex()+s.getWidth();
        newIndex = spaceIndex;
        // Modify current level's spaces
        if(modifyLeft) {
            this.spaces.get(this.current).add(spaceIndex++, new Space(s.getIndex(), leftSide-s.getIndex()));
            this.spacesInRow[this.current]++;
            this.skipSpace = true;
        }

        if(modifyRight){
            this.spaces.get(this.current).add(spaceIndex,
                    new Space(rightSide+1, s.getIndex() + s.getWidth() - rightSide - 1));
            this.spacesInRow[this.current]++;
        }

        return newIndex;
    }

    /**
     * Removes the block and merges the surrounding space(s).
     *
     * @param m the Move to be undone.
     * @param spaceIndex the space in which to undo it.
     * @precondition m and spaceIndex describe a block that has already been placed.
     */
    void removeBlockUpdate(Move m, int spaceIndex){
        int leftSide = m.getIndex() - 1, rightSide = m.getIndex() + m.getWidth();
        boolean leftInBounds = leftSide >= 0, rightInBounds = rightSide < this.width,
                leftSpaceFree = false, rightSpaceFree = false, // is there a zero two spaces to a side?
                blockToLeft = false, blockToRight = false, // is there a block two spaces to a side?
                leftOverhang = false, rightOverhang = false; // left/right sides would create overhang?

        // Initializations
        if(leftInBounds){
            leftOverhang = !this.blocks[this.current + 1][leftSide]; // if nothing there
            if(leftSide > 0){
                blockToLeft = this.blocks[this.current][leftSide - 1];
                // check for immediate overhang, see if the space in question would be suspended, and check for space
                leftSpaceFree = !leftOverhang && this.blocks[this.current + 1][leftSide - 1] &&
                        !blockToLeft && !this.unavailableColumn[leftSide - 1];
            }
        }

        if(rightInBounds){
            rightOverhang = !this.blocks[this.current + 1][rightSide];
            if(rightSide < this.width - 1){
                blockToRight = this.blocks[this.current][rightSide + 1];
                rightSpaceFree = !rightOverhang && this.blocks[this.current + 1][rightSide + 1] &&
                        !blockToRight && !this.unavailableColumn[rightSide + 1];
            }
        }

        // if left overhang or at edge and not a block to left / right
        boolean shouldIncLeft = leftOverhang || !leftInBounds || (leftSide > 0 && blockToLeft);
        boolean shouldDecRight = rightOverhang || !rightInBounds || (rightSide < this.width-1 && blockToRight);

        // Remove the block
        this.lastIDEven = !this.lastIDEven;
        this.lastID--;
        for(int i = m.getIndex(); i < rightSide; i++)
            this.blocks[this.current][i] = false;

        // Remove space above
        modifySpaceAbove(m, false);
        this.placedInRow[this.current]--;

        // mark as available if this block was originator of unavailability
        if(leftInBounds && !blockToLeft && !leftOverhang)
            this.unavailableColumn[leftSide] = false;
        if(rightInBounds && !blockToRight && !rightOverhang)
            this.unavailableColumn[rightSide] = false;

        // Adjust dimensions of soon-to-be-added space
        if(shouldIncLeft)
            leftSide++;
        if(shouldDecRight)
            rightSide--;

        // use s as one of pre-existing spaces that will be merged
        Space s;
        // width gets incremented because if lb == rb == 0, it's a one-width block
        Space newSpace = new Space(leftSide, rightSide - leftSide + 1);

        if (leftSpaceFree || rightSpaceFree){
            s = this.spaces.get(this.current).get(spaceIndex);
            if(leftSpaceFree){
                newSpace.setIndex(s.getIndex());
                newSpace.setWidth(newSpace.getWidth() + s.getWidth());
                this.spaces.get(this.current).remove(spaceIndex);
                this.spacesInRow[this.current]--;
            }

            // if open block to right, there must be a space
            if(rightSpaceFree){
                newSpace.setWidth(newSpace.getWidth() +
                        this.spaces.get(current).get(spaceIndex).getWidth());
                this.spaces.get(this.current).remove(spaceIndex);
                this.spacesInRow[this.current]--;
            }
        }
        // Add the space
        this.spaces.get(this.current).add(spaceIndex, newSpace);
        this.spacesInRow[this.current]++;
    }


    /**
     * Add or remove the space to/from the row above the given move. If already on the last row, does nothing.
     *
     * @precondition if a remove operation is desired, the space must be last in the LinkedList.
     */
    private void modifySpaceAbove(Move m, boolean addTrue){
        int above = this.current - 1;
        if(above < 0)
            return;
        if(addTrue){
            this.spaces.get(above).add(new Space(m.getIndex(), m.getWidth()));
            this.spacesInRow[above]++;
        } else {
            this.spaces.get(above).remove(spaces.get(above).size() - 1);
            this.spacesInRow[above]--;
        }
    }


    /**
     * Advance to the next row in the castle - that is, stop placing blocks in the current row.
     */
    void advanceRow(){
        this.current--;
    }

    void retreatRow(){
        this.current++;
    }

    int getLastID() { return this.lastID; }

    boolean lastIDEven(){
        return this.lastIDEven;
    }

    boolean areBlocksInLastRow(){
        return this.placedInRow[0] > 0;
    }

    boolean inLastRow(){
        return this.current == 0;
    }

    boolean isEvenSolution(){
        return this.areBlocksInLastRow() && this.lastIDEven();
    }

    boolean isOddSolution(){
        return this.areBlocksInLastRow() && !this.lastIDEven();
    }

    boolean canAddBlock(){
        return this.spacesInRow[this.current] > 0;
    }

    boolean canAdvance(){
        return !this.inLastRow() && this.placedInRow[this.current] > 0;
    }

    /**
     * Debugging visualization.
     *
     * @param showSpaces toggles whether the current row's spaces are displayed underneath the castle.
     */
    public void display(boolean showSpaces){
        // Each row
        for(int i = 0; i < this.height; i++) {
            // Each col
            for(int j = 0; j < width; j++){
                if(this.blocks[i][j])
                    System.out.print("█");
                else
                    System.out.print(" ");
            }
            if(this.current == i)
                System.out.print("c");
            System.out.println();
        }

        // Show which columns are invalid
        for(Boolean bool : this.unavailableColumn){
            if(bool)
                System.out.print("X");
            else
                System.out.print(" ");
        }
        System.out.println();

        if(showSpaces)
            this.displaySpaces();
        System.out.println();
    }

    // Displays the castle with the proposed move displayed as 'X'
    public void displayMove(Move m){
        int col = m.getIndex(), upper = col + m.getWidth();
        // Each row
        for(int i = 0; i < this.height; i++) {
            // Each col
            for(int j = 0; j < this.width; j++){
                if(this.current == i && j < upper && j >= col)
                    System.out.print("X");
                else {
                    if(this.blocks[i][j])
                        System.out.print("█");
                    else
                        System.out.print(" ");
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    private void displaySpaces(){
        System.out.println("Space list for the current row:");
        this.spaces.get(this.current).forEach(Space::printSpace);
    }
}

class Move {
    private int index;
    private int width;

    Move(int i, int w){
        this.index = i;
        this.width = w;
    }

    int getIndex(){
        return this.index;
    }

    int getWidth(){
        return this.width;
    }
}

/**
 * @example
 * Space(2, 3) corresponds to
 *
 * X-SSS
 * XXXXX
 */
class Space {
    private int index; // where the space starts
    private int width; // area in which you can place blocks

    Space(int i, int w){
        this.index = i;
        this.width = w;
    }

    int getIndex(){
        return this.index;
    }

    int getWidth(){
        return this.width;
    }

    void setIndex(int i){
        this.index = i;
    }

    void setWidth(int w){
        this.width = w;
    }

    void printSpace(){
        System.out.println("Space: [index " + this.index + ", width " + this.width + "]");
    }
}

/**
 * Allows manipulation of the twin data points required by memoisation. Scales infinitely past the limits of the
 * Integer type.
 */
class Result{
    private BigInteger evenSolutions;
    private BigInteger oddSolutions;

    Result(){
        this.evenSolutions = BigInteger.ZERO;
        this.oddSolutions = BigInteger.ZERO;
    }

    Result(int even, int odd) {
        this.evenSolutions = BigInteger.valueOf(even);
        this.oddSolutions = BigInteger.valueOf(odd);
    }

    Result(BigInteger even, BigInteger odd) {
        this.evenSolutions = even;
        this.oddSolutions = odd;
    }

    void incrementEven(){
        this.evenSolutions = this.evenSolutions.add(BigInteger.ONE);
    }

    void incrementOdd(){
        this.oddSolutions = this.oddSolutions.add(BigInteger.ONE);
    }

    void addResult(Result toAdd){
        this.evenSolutions = this.evenSolutions.add(toAdd.evenSolutions);
        this.oddSolutions = this.oddSolutions.add(toAdd.oddSolutions);
    }

    /**
     * Swap the even and odd solutions of the Result.
     */
    Result flip(){
        return new Result(this.oddSolutions, this.evenSolutions);
    }

    /**
     * Display in the format of {even, odd}
     */
    void display(){
        System.out.print("{" + evenSolutions.toString() + ", " + oddSolutions.toString() + "}");
    }
}

/**
 * For each possible distribution of spaces, cache the results.
 *
 * The TRIE data structure optimizes storage efficiency, but I predict that the space complexity will
 * nonetheless remain prohibitive. Additionally, due to the nature of transferring solutions from a smaller castle
 * to a larger castle, the odd solution must also be counted; the even and odd solutions must be swapped before addition
 * to the caller's tally.
 *
 * Example (X := block, - := inaccessible open space):
 *  -
 *  -
 * X-
 * XXXX
 *
 * The TRIE will be accessed by sorting the spaces into descending order (by width and then by height, including
 * includes the base block below the given space): memoisedResults[w1][h1][w2][h2]. Thus, the above castle can be
 * represented by [2][4][1][3].
 */
class ResultTRIE{
    Result data;
    ArrayList<ResultTRIE> children; // do array to test it works?

    // Cache a new result
    public ResultTRIE(Result toCache){
        this.data = toCache;
        this.children = new ArrayList<ResultTRIE>();
    }

    // Navigate through the structure and cache the data
    public void setTRIE(ArrayList<Integer> list, Result toCache){
        if(list.isEmpty()){
            this.data = toCache;
            this.children = new ArrayList<ResultTRIE>();
        }

    }

    Result getResult(ArrayList<Integer> list){
        if(list.isEmpty())
            return this.data;
        else
            return this.children.get(list.remove(0)).getResult(list); // is this destructive of original list?
    }
}