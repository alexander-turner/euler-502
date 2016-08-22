import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/*
Project Euler: Problem 502 (faster solution in progress is commented out)
https://projecteuler.net/problem=502
 
A block is defined as a rectangle with a height of 1 and an integer-valued length. Let a castle be a configuration of stacked blocks.

Given a game grid that is w units wide and h units tall, a castle is generated according to the following rules:

1 Blocks can be placed on top of other blocks as long as nothing sticks out past the edges or hangs out over open space.
2 All blocks are aligned/snapped to the grid.
3 Any two neighboring blocks on the same row have at least one unit of space between them.
4 The bottom row is occupied by a block of length w.
5 The maximum achieved height of the entire castle is exactly h.
6 The castle is made from an even number of blocks.

Let F(w,h) represent the number of valid castles, given grid parameters w and h.
For example, F(4,2) = 10, F(13,10) = 3729050610636, F(10,13) = 37959702514, and F(100,100) mod 1 000 000 007 = 841913936.

Summary: the algorithm calculates the number of castles on a w*h grid which satisfy the above conditions. 
The algorithm must be fast enough to calculate (F(1012,100) + F(10000,10000) + F(100,1012)) mod 1 000 000 007.

Completed improvements:
 * DONE: Eliminated need to create an exponential number of duplicate castles
 * DONE: Enabled dynamic updating of spaces
 * DONE: Decrease from n! to something much better by not repeating indices 
 * DONE: Improved correctness of recursive algorithm
 * DONE: Implemented an invalid column array to save up to O(w*h) accesses each advancement
 * DONE: Implemented BigIntegers to accommodate arbitrary-length integers
 * DONE: Store blocks as boolean instead of int to cut castle space requirement by 4
 * DONE: Store the number of spaces in each row to cut out an O(w) operation each iteration
 * DONE: Store whether current ID is even, instead of what the lastID was - save a modulo operation each time
 * DONE: Cleaned up and standardized class methods and variables
 * DONE: Fixed algorithmic correctness. Previously, blocks were treated as if their placement order
          mattered for generating distinct castles. The order doesn't matter; the algorithm is now 
          much less complex.

Efficiency:
 * Cache castle even/odd solutions for each w*h combo. Last space in last row can definitely be memoised
 * Complete wrapper function for memoiseCastle
 * Know which castles must be pre-calculated, add them in the correct order, fibonacci-style.
 */
public class fivehundredtwo {
    // width, height
    private static Castle globalCastle = new Castle(4,13);

    /*
     * Purpose:
     *  cachedMovesRec[i] contains the moves available for a free space of size i that weren't available
     *   at the smaller size spaces.
     *   FORMAT: Move m(column, width)
     *   e.g. at cachedMoves.get(1): (0,1), (1,1), (2,1), (3,1) for w=4
     */
    private static List<ArrayList<Move>> cachedMovesRec = new ArrayList<>(globalCastle.width+1);

    // IN PROGRESS: castleResults[x][y] stores even and odd solutions for castles of dimensions x by y (counting the base)
    private static Result[][] castleResults = new Result[globalCastle.width+1][globalCastle.height+1];
    // Set to 1000 as temporary number
    private static int[][][] blockNumberResults = new int[globalCastle.width+1][globalCastle.height+1][1000];

    public static void main(String[] args) {
        prepCachedMovesRec();
        System.out.println("Benchmarks for a " + globalCastle.width + "w x " +
                globalCastle.height + "h castle:");

        int widthBound = globalCastle.width, heightBound = globalCastle.height;
        System.out.println("Iterating over various castle sizes (whose dimensions do not exceed " + widthBound + " by " + heightBound + ")");


        // iterate across a width value
        for(int i=1; i <= widthBound; i++){
            //System.out.print(i + "| ");
            for(int j=1; j<=heightBound; j++){
                if(j==1){
                    castleResults[i][j] = new Result(0, 1);
                } else if(i==1){ // special case where we can predetermine results
                    castleResults[i][j] = new Result((j+1)%2, j%2);
                } else {
                    globalCastle = new Castle(i,j);
                    castleResults[i][j] = memoiseCastleCorrect(0);
                }
                castleResults[i][j].display(); // integrate into function itself?
                if(j<heightBound)
                    System.out.print("; ");
            }
            System.out.println();
        }

        // iterate across a height value
		/*for(int i=2; i <= heightBound; i++){
			for(int j=1; j<=widthBound; j++){
			System.out.println("W: " + j + " h: " + i);
			if(j==1){ // special case where we can predetermine results
				castleResults[j][i] = new Result((i+1)%2, i%2);
			} else {
				globalCastle = new Castle(j,i);
				castleResults[j][i] = memoiseCastleCorrect(0);
			}
			castleResults[j][i].display(); // integrate into function itself?
			}
		System.out.println();
		}*/
		/*prepCachedMoves();
		for(int i=1; i<=widthBound; i++){
			for(int j=2; j<=heightBound; j++){
				System.out.println("W: " + i + " h: " + j);
				if(i==1){ // special case where we can predetermine results
					castleResults[i][j] = new Result((j+1)%2, j%2);
				} else {
					globalCastle = new Castle(i,j);
					castleResults[i][j] = memoiseCastle(0);
				}
				castleResults[i][j].display(); // integrate into function itself?
				memoiseCastleCorrect(0).display();
			}
			System.out.println();
		}*/
    }

    // Cache the moves for the current width of globalCastle
    private static void prepCachedMovesRec(){
        cachedMovesRec = new ArrayList<>(globalCastle.width+1);
        cachedMovesRec.add(new ArrayList<>());
        for(int size=1; size<=globalCastle.width; size++){
            // initialize ArrayList of moves
            cachedMovesRec.add(new ArrayList<>());
            for(int i=0; i<=globalCastle.width-size; i++)
                cachedMovesRec.get(size).add(new Move(i, size));
        }
    }

    /*
     * Recursively enumerate castles for the current globalCastle.
     *  Pre-conditions:
     *   globalCastle is a valid, existing castle.
     *   cachedMovesRec is generated.
     *  Post-conditions:
     *   returns the number of satisfactory castles for the given starting configuratio.
     */
    private static BigInteger enumerateCastleRec(int spaceIndex){
        BigInteger sum = BigInteger.ZERO;
        if(globalCastle.isEvenSolution())
            sum = sum.add(BigInteger.ONE);

        // Try advancing a row
        if (globalCastle.canAdvance()){
            globalCastle.advanceRow();
            sum = sum.add(enumerateCastleRec(0));
            globalCastle.retreatRow();
        }

        if(globalCastle.canAddBlock()) {
            int lastSpaceIndex;

            for(; spaceIndex < globalCastle.spacesInRow[globalCastle.current]; spaceIndex++){
                Space s = globalCastle.spaces.get(globalCastle.current).get(spaceIndex);
                int spaceSize = s.getWidth();
                // Execute the possible moves
                for(int first = 1; first <= spaceSize; first++)
                    for(int second=0; second <= spaceSize - first; second++) {
                        Move m = cachedMovesRec.get(first).get(second),
                                // increment by current index; cachedMovesRec doesn't account for offset from current block
                                nextMove = new Move(m.getIndex() + s.getIndex(), m.getWidth());

                        lastSpaceIndex = globalCastle.placeBlockUpdate(nextMove, spaceIndex);

                        // ensure we move to the next space instead of repeating possibilities
                        if(globalCastle.skipSpace){
                            globalCastle.skipSpace = false;
                            sum = sum.add(enumerateCastleRec(lastSpaceIndex+1));
                        } else {
                            sum = sum.add(enumerateCastleRec(lastSpaceIndex));
                        }
                        globalCastle.removeBlockUpdate(nextMove, lastSpaceIndex);
                    }}}

        return sum;
    }


    // IN PROGRESS: wrapper for the memoiseCastle function
    private static Result memoiseCastleWrapper(int w, int h){
        // we can calculate this kind of castle by formula
        if(w == 1)
            return new Result((h+1)%2, h%2);
        // set the globals
        // create the castle - add check
        globalCastle = new Castle(w, h);
        // pre-generate available moves - ADD check for whether already done
        prepCachedMovesRec();
        // rock and roll
        return memoiseCastle(0);
    }

    /* IN PROGRESS: try speeding up the process by storing results for each castle size
     *  and using that information to calculate how many combinations are possible from
     *  a given configuration.
     */
    private static Result memoiseCastle(int spaceIndex){
        Result sum = new Result();
        if(globalCastle.isSolution()){
            if(globalCastle.blockIDEven())
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
                    if(globalCastle.blockIDEven())
                        sum.addResult(castleResults[spaceSize][projectedHeight].flip());
                    else
                        sum.addResult(castleResults[spaceSize][projectedHeight]);
                } else {
                    // Execute the possible moves
                    for(int first = 1; first <= spaceSize; first++) {
                        for(int second=0; second <= spaceSize - first; second++) {
                            Move m = cachedMovesRec.get(first).get(second),
                                    // increment by current index; cachedMovesRec doesn't account for offset from current block
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

    // DEBUGGING: a version of enumerateCastleRec that also counts up the odd-numbered solutions
    private static Result memoiseCastleCorrect(int spaceIndex){
        Result sum = new Result();
        if(globalCastle.isSolution()){
            blockNumberResults[globalCastle.width][globalCastle.height][globalCastle.lastID]++;
            if(globalCastle.blockIDEven())
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
                    for(int second=0; second <= spaceSize - first; second++) {
                        Move m = cachedMovesRec.get(first).get(second),
                                // increment by current index; cachedMovesRec doesn't account for offset from current block
                                nextMove = new Move(m.getIndex() + s.getIndex(), m.getWidth());

                        lastSpaceIndex = globalCastle.placeBlockUpdate(nextMove, spaceIndex);
                        if(globalCastle.skipSpace){
                            globalCastle.skipSpace = false;
                            sum.addResult(memoiseCastleCorrect(lastSpaceIndex+1));
                        } else {
                            sum.addResult(memoiseCastleCorrect(lastSpaceIndex));
                        }
                        globalCastle.removeBlockUpdate(nextMove, lastSpaceIndex);
                    }}}

        if (globalCastle.canAdvance()){
            globalCastle.advanceRow();
            sum.addResult(memoiseCastleCorrect(0));
            globalCastle.retreatRow();
        }

        return sum;
    }

}

class Castle{
    // Castle dimensions
    int height, width;
    int current; // the current row index
    int lastID;
    private int placedInRow[]; // how many blocks have been placed in each row
    int spacesInRow[]; // how many spaces are in each row
    boolean skipSpace; // global flag for knowing when to move to next space
    private boolean lastIDEven; // track whether the ID of the last block placed is even
    private boolean unavailableColumn[];
    List<ArrayList<Space>> spaces; // track available spaces for each level - update while placing / removing blocks

    /*
     *  Tracks block IDs.
     *   true if a block is there
     */
    private boolean[][] blocks;

    /* Construct a Castle with only the bottom row filled in
     * Pre-condition: h > 0 && w > 0
     */
    Castle(int w, int h){
        this.width = w;
        this.unavailableColumn = new boolean[this.width];
        this.height = h;
        this.current = this.height-1;
        this.lastIDEven = true;
        this.lastID = 0;
        this.placedInRow = new int[this.height];
        this.spacesInRow = new int[this.height];
        this.spaces = new ArrayList<>(this.height);
        for(int i=0; i<this.height; i++)
            this.spaces.add(new ArrayList<>());
        this.skipSpace = false;
        this.blocks = new boolean[this.height][this.width];
        // Place first space and block
        this.spaces.get(this.current).add(new Space(0, this.width));
        this.spacesInRow[this.current]++;
        placeBlockUpdate(new Move(0, this.width), 0); // remove for a bit of efficiency? just pretend it's there
        // Leave first row
        this.current--;
    }

    /* Assumes that the space is open since the input is given as a constructed Move
     * Returns index of first space (-1 if no spaces remain)
     */
    int placeBlockUpdate(Move m, int spaceIndex){
        int leftSide = m.getIndex()-1, rightSide = m.getIndex()+m.getWidth(), returnVal;

        // Lay the block
        this.lastIDEven = !this.lastIDEven;
        this.lastID++;

        for(int i=m.getIndex(); i<rightSide; i++)
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
        returnVal = spaceIndex;
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

        //if(originalSpaces < this.spacesInRow[this.current])
        //	this.skipSpace = true;

        return returnVal;
    }

    // Removes the block and merges the surrounding space(s)
    void removeBlockUpdate(Move m, int spaceIndex){
        int leftSide = m.getIndex()-1, rightSide = m.getIndex()+m.getWidth();
        boolean leftInBounds = leftSide >= 0, rightInBounds = rightSide < this.width,
                leftSpaceFree = false, rightSpaceFree = false, // is there a zero two spaces to a side?
                blockToLeft = false, blockToRight = false, // is there a block two spaces to a side?
                leftOverhang = false, rightOverhang = false; // left/right sides would create overhang?

        // Initializations
        if(leftInBounds){
            leftOverhang = !this.blocks[this.current+1][leftSide]; // if nothing there
            if(leftSide > 0){
                blockToLeft = this.blocks[this.current][leftSide-1];
                // check for immediate overhang, see if the space in question would be suspended, and check for space
                leftSpaceFree = !leftOverhang && this.blocks[this.current+1][leftSide-1] &&
                        !blockToLeft && !this.unavailableColumn[leftSide-1];
            }
        }

        if(rightInBounds){
            rightOverhang = !this.blocks[this.current+1][rightSide];
            if(rightSide < this.width-1){
                blockToRight = this.blocks[this.current][rightSide+1];
                rightSpaceFree = !rightOverhang && this.blocks[this.current+1][rightSide+1] &&
                        !blockToRight && !this.unavailableColumn[rightSide+1];
            }
        }

        // if left overhang or at edge and not a block to left / right
        boolean shouldIncLeft = leftOverhang || !leftInBounds || (leftSide > 0 && blockToLeft);
        boolean shouldDecRight = rightOverhang || !rightInBounds || (rightSide < this.width-1 && blockToRight);

        // Remove the block
        this.lastIDEven = !this.lastIDEven;
        this.lastID--;
        for(int i=m.getIndex(); i<rightSide; i++)
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
        // width gets one added because if lb == rb == 0, it's a one-width block
        Space newSpace = new Space(leftSide, rightSide - leftSide + 1);

        if (leftSpaceFree || rightSpaceFree){
            s = this.spaces.get(this.current).get(spaceIndex);
            if(leftSpaceFree){// is to left of move
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


    /*
     * Add or remove the space to/from the row above the given move.
     *  If already on the last row, does nothing.
     * Pre-conditions:
     *  If a remove operation is desired, the space must be last in the LinkedList.
     */
    private void modifySpaceAbove(Move m, boolean addTrue){
        int above = this.current-1;
        if(above < 0)
            return;
        if(addTrue){
            this.spaces.get(above).add(new Space(m.getIndex(), m.getWidth()));
            this.spacesInRow[above]++;
        }
        else {
            this.spaces.get(above).remove(spaces.get(above).size()-1);
            this.spacesInRow[above]--;
        }
    }


    /*
     * Purpose:
     *  Move on to the next row in the castle; stop placing blocks at the current row.
     * Post-conditions:
     *  For every value zero or below in the current row, decrement and decrement above it
     */
    void advanceRow(){
        int endIndex;
        for(Space s : this.spaces.get(this.current)){
            endIndex = s.getIndex() + s.getWidth();
            for(int i=s.getIndex(); i < endIndex; i++)
                this.unavailableColumn[i] = true;
        }
        this.current--;
    }

    /*
     * Purpose:
     *  Move back to the prior row in the castle; reverse effects of finishing the prior row.
     * Post-conditions:
     *  Re-validates columns previously unavailable due to spaces / clears all rows above with zeroes
     */
    void retreatRow(){
        this.placedInRow[this.current] = 0;
        this.current++;

        int endIndex;
        for(Space s : this.spaces.get(this.current)){
            endIndex = s.getIndex() + s.getWidth();
            for(int i=s.getIndex(); i < endIndex; i++)
                this.unavailableColumn[i] = false;
        }
    }

    boolean blockIDEven(){
        return this.lastIDEven;
    }

    // Warning: doesn't check if it has an even number of blocks
    boolean isSolution(){
        return this.placedInRow[0] > 0;
    }

    // Returns whether the castle has an even number of blocks
    boolean isEvenSolution(){
        return this.isSolution() && this.blockIDEven();
    }

    // Returns whether the current row has room for blocks
    boolean canAddBlock(){
        return this.spacesInRow[this.current] > 0;
    }

    // Returns whether we can advance to the next row
    boolean canAdvance(){
        return this.current > 0 && this.placedInRow[this.current] > 0;
    }

    // Display the castle
    public void display(boolean showSpaces){
        // Each row
        for(int i=0; i<this.height; i++) {
            // Each col
            for(int j=0; j<width; j++){
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
        for(int i=0; i<this.height; i++) {
            // Each col
            for(int j=0; j<this.width; j++){
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

    // display spaces for the current level
    private void displaySpaces(){
        System.out.println("Space list for the current row:");
        for(Space s : this.spaces.get(this.current))
            s.printSpace();
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

    public void printMove(){
        System.out.println("Move: [index " + this.index + ", width " + this.width + "]");
    }
}

class Space{
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

    // Swap the even and odd solutions of the returned instance
    Result flip(){
        return new Result(this.oddSolutions, this.evenSolutions);
    }

    // display {even, odd}
    void display(){
        System.out.print("{" + evenSolutions.toString() + ", " + oddSolutions.toString() + "}");
    }
}

/*
 * For each possible distribution of spaces, cache the results.
 * Navigate using a TRIE structure, alternating between width and height for each measurement.
 * A measurement is demarcated by a base block and all of the empty space above it.
 * Example (spaces highlighted by X):
 *  X XX
 *  X XX
 *  █ XX
 *  ████
 * These two measurements (format: [w,h]) are: [1,3] and [2,4].
 * The spaces must be sorted by width: [2,4] and [1,3].
 * Representing as an array for simplicity, we access the TRIE with coordinates:
 *  [2][4][1][3]
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