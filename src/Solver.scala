import scala.annotation.tailrec

object Solver {
  /** A possibility is a sequence that is a possible pattern on a row/column. */
  type Possibility = Seq[Entry]

  /** Return all possible patterns for a row/column given its lengths and the row/column width */
  def findRowPossibilities (index: Int, isRow: Boolean, allLengths: Seq[Int], width: Int): Seq[Possibility] = {
    /** Find all possibilities with the given lengths remaining to be allocated to the right of an allocated section (prefix). */
    def findRowPossibilitiesFollowing (lengths: Seq[Int], prefix: Possibility): Seq[Possibility] = {
      /** Create a series of entries of a given length from a given offset on the row, either filled or empty. */
      def cells (length: Int, filled: Boolean, offset: Int) =
        Seq.tabulate (length)(pos => if (isRow) Entry (index, offset + pos, filled) else Entry (offset + pos, index, filled))

      /** Create a series of entries for this row from a given offset, which is a sequence of empty cells followed by a
        * sequence of filled cells and then another sequence of empty. */
      def delimitedLength (left: Int, filled: Int, right: Boolean, offset: Int): Possibility =
          cells (left, false, offset) ++ cells (filled, true, offset + left) ++
            (if (right) cells (1, false, offset + left + filled) else Nil)

      /* The possibility for a row remainder with no lengths is just empty cells to the end of the row.
       * Otherwise, generate the possibilities by choosing different positions for the first length, then recursively
       * applying this function for the rest of the lengths. */
      if (lengths.isEmpty)
        Seq (prefix ++ cells (width - prefix.length, false, prefix.length))
      else
        (for (gap <- 0 to (width - prefix.length - lengths.sum - lengths.size + 1)) yield
          findRowPossibilitiesFollowing (lengths.tail, prefix ++
            delimitedLength (gap, lengths.head, lengths.tail.nonEmpty, prefix.length))
          ).flatten
    }

    // Find all possibilities for this row containing all lengths with no cells initially assigned.
    findRowPossibilitiesFollowing (allLengths, Nil)
  }

  /** Generate all the possibilities from each row and each column in the grid, given the grid's dimensions and the lengths. */
  def allPossibilities (width: Int, allLengths: Seq[Seq[Int]], isRow: Boolean): Seq[Possibility] =
    allLengths.zipWithIndex.flatMap {
      case (lengths, index) => findRowPossibilities (index, isRow, lengths, width)
    }

  /** Find each position with only one possible entry (empty or filled) in all the given possibilities, return those entries. */
  def certainties (possibilities: Seq[Possibility]): Set[Entry] =
    possibilities.flatten.groupBy {
      e => (e.row, e.column)
    }.mapValues (_.map (_.filled).toSet).filter (_._2.size == 1).map {
      p => Entry (p._1._1, p._1._2, p._2.head)
    }.toSet

  /** Filters the row/column possibilities to those that do not contain cells contradicting the given entries, returning
    * those remaining possibilities only if all rows/columns still have at least one possibility, else None. */
  def filter (possibilities: Seq[Possibility], known: Set[Entry], height: Int, isRow: Boolean): Seq[Possibility] =
    possibilities.filter (!_.exists (entry => known.contains (entry.opposite)))

  def feasible (width: Int, height: Int, rowPossibilities: Seq[Possibility], colPossibilities: Seq[Possibility]): Boolean =
    (0 until height).forall (row => rowPossibilities.exists (_.head.row == row)) &&
      (0 until width).forall (col => colPossibilities.exists (_.head.column == col))

  def solve (rowLengths: Seq[Seq[Int]], columnLengths: Seq[Seq[Int]]): Seq[Set[Entry]] = {
    val width = columnLengths.size
    val height = rowLengths.size
    /** All coordinates in the grid */
    val points = Seq.tabulate (height, width)((_, _)).flatten

    /** Find all solutions from a set of alternative rows and a set of alternative columns, given a set of assumed
      * grid entries, and include a set of pre-found solutions in the results. */
    @tailrec def search (rowAlternatives: Seq[Possibility], colAlternatives: Seq[Possibility],
                assumptions: Seq[Set[Entry]], found: Seq[Set[Entry]]): Seq[Set[Entry]] = {
      @tailrec def deduce (rowPossibilities: Seq[Possibility], colPossibilities: Seq[Possibility], previouslyKnown: Set[Entry]): Option[Set[Entry]] = {
        val rowsFiltered = filter (rowPossibilities, previouslyKnown, height, true)
        val colsFiltered = filter (colPossibilities, previouslyKnown, width, false)
        val known = certainties (rowsFiltered) ++ certainties (colsFiltered) ++ previouslyKnown
        if (feasible (width, height, rowsFiltered, colsFiltered))
          if (known.size > previouslyKnown.size)
            deduce (rowsFiltered, colsFiltered, known)
          else
            Some (known)
        else
          None
      }

      val solutions = assumptions.flatMap { assumption =>
        deduce (rowAlternatives, colAlternatives, assumption).map { solution =>
          (assumption, solution)
        }
      }
      val isComplete = solutions.partition (_._2.size == width * height)

      if (isComplete._2.isEmpty)
        found ++ isComplete._1.map (_._2)
      else {
        val nextTries = isComplete._2.flatMap { partial =>
          points.find (point => !partial._2.exists (entry => entry.row == point._1 && entry.column == point._2)).map { unknown =>
            Seq (partial._1 ++ partial._2 + Entry (unknown._1, unknown._2, true),
              partial._1 ++ partial._2 + Entry (unknown._1, unknown._2, false))
          }
        }.flatten
        search (rowAlternatives, colAlternatives, nextTries, found ++ isComplete._1.map (_._2))
      }
    }

    search (allPossibilities (width, rowLengths, true), allPossibilities (height, columnLengths, false), Seq (Set[Entry] ()), Nil)
  }
}
