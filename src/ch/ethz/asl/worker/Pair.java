package ch.ethz.asl.worker;

/**
 * A Pair class used for returning two values from a Java function.
 * 
 * Taken from https://stackoverflow.com/questions/521171/a-java-collection-of-value-pairs-tuples
 */
public class Pair<L,R> {

	  public final L left;
	  public final R right;

	  public Pair(L left, R right) {
	    this.left = left;
	    this.right = right;
	  }

	  @Override
	  public int hashCode() { return left.hashCode() ^ right.hashCode(); }

	  @Override
	  public boolean equals(Object o) {
	    if (!(o instanceof Pair)) return false;
	    Pair pairo = (Pair) o;
	    return this.left.equals(pairo.left) &&
	           this.right.equals(pairo.right);
	  }

	}