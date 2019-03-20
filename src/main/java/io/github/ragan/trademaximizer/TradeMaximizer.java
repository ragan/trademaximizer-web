// TradeMaximizer.java
// Created by Chris Okasaki (cokasaki)
// Version 1.3a
// $LastChangedDate: 2008-03-07 09:08:08 -0500 (Fri, 07 Mar 2008) $
// $LastChangedRevision: 28 $
package io.github.ragan.trademaximizer;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

interface Metrics {
  int calculate(List<List<Graph.Vertex>> cycles);
}

class MetricSumSquares implements Metrics {
  private List<List<Graph.Vertex>> cycles;
  private int sumOfSquares;

  public int calculate(List<List<Graph.Vertex>> cycles) {
    sumOfSquares = 0;
    for (List<Graph.Vertex> cycle: cycles) sumOfSquares += cycle.size()*cycle.size();
    this.cycles = cycles;
    return sumOfSquares;
  }

  public String toString() {
    int[] groups = new int[cycles.size()];
    String str = "[ ";

    for (int j = 0; j < cycles.size(); j++)
      groups[j] = cycles.get(j).size();

    Arrays.sort(groups);

    str = str + sumOfSquares + " :";

    for (int j = groups.length-1; j >= 0; j--)
      str = str + " " + groups[j];

    return str + " ]";
  }
};

class MetricUsersTrading implements Metrics {
  private int count;

  public int calculate(List<List<Graph.Vertex>> cycles) {
    HashSet<String> users = new HashSet<String>();

    for (List<Graph.Vertex> cycle : cycles)
        for(Graph.Vertex vert : cycle)
            users.add(vert.user);

    count = users.size();

    return -count;
  }

  public String toString() {
    return "[ users trading = " + count + " ]";
  }
};

class MetricFavorUser implements Metrics {
  private String user;
  private int count;

  public MetricFavorUser(String user) { this.user = "(" + user + ")"; }

  public int calculate(List<List<Graph.Vertex>> cycles) {
    Map<String, Integer> users = new HashMap<String, Integer>();
    Integer xyz = null;
    int sum = 0;

    for (List<Graph.Vertex> cycle : cycles)
        for(Graph.Vertex vert : cycle) {
	    String user = vert.user.toUpperCase();
            users.put(user, 1 + (users.containsKey(user) ? users.get(user) : 0));
	}

    xyz = users.get(this.user);
    if( xyz != null )
      count = xyz.intValue();
    else
      count = 0;

    return -count;
  }

  public String toString() {
    return "[ " + user + " trading = " + count + " ]";
  }
};

class MetricUsersSumOfSquares implements Metrics {
  private int sum;
  private int count;

  public int calculate(List<List<Graph.Vertex>> cycles) {
    Map<String, Integer> users = new HashMap<String, Integer>();
    sum = 0;

    for (List<Graph.Vertex> cycle : cycles)
        for(Graph.Vertex vert : cycle)
            users.put(vert.user, 1 + (users.containsKey(vert.user) ? users.get(vert.user) : 0));

    count = users.size();

    for (Integer user : users.values())
        sum += user.intValue()*user.intValue();
	//if (debug) {
        //  System.out.print("Sizes:");
        //  for (Integer user : users.values())
        //    System.out.print(" " + user.intValue());
        //  logger.log();
        //}

    return sum;
  }

  public String toString() {
    return "[ users trading = " + count + ", sum of squares = " + sum + " ]";
  }
};

public class TradeMaximizer {
  public static void main(String[] args) throws IOException { new TradeMaximizer().run(args, System.in, System.out); }
  
  final String version = "Version 1.4a";

  Metrics metric = new MetricSumSquares();

  public void run(String[] args, String in) throws IOException {
    run(args, new ByteArrayInputStream(in.getBytes()), System.out);
  }

  public void run(String[] args, InputStream istream, OutputStream ostream) throws IOException {
    Logger logger = new Logger(ostream);
    logger.log("TradeMaximizer " + version);


    List< String[] > wantLists = readWantLists(istream, new FatalError(ostream));
    if (wantLists == null) return;
    if (options.size() > 0) {
      logger.log("Options:");
      for (String option : options) logger.log(" "+option);
        logger.log("\n");
    }
    logger.log("\n");

    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      for (String[] wset : wantLists) {
        for (String w : wset) {
  	  digest.update((byte)' ');
  	  digest.update(w.getBytes());
        }
  	digest.update((byte)'\n');
      }
      logger.log("Input Checksum: " + toHexString(digest.digest()));
    }
    catch (NoSuchAlgorithmException ex) { }

//    parseArgs(args, true);

    if( iterations > 1 && seed == -1 ) {
      seed = System.currentTimeMillis();
      logger.log("No explicit SEED, using " + seed);
    }

    if ( ! (metric instanceof MetricSumSquares) && priorityScheme != NO_PRIORITIES )
      logger.log("Warning: using priorities with the non-default metric is normally worthless");

    buildGraph(wantLists);
    if (showMissing && officialNames != null && officialNames.size() > 0) {
      for (String name : usedNames) officialNames.remove(name);
      List<String> missing = new ArrayList<String>(officialNames);
      Collections.sort(missing);
      for (String name : missing) {
        logger.log("**** Missing want list for official name " +name);
      }
      logger.log("\n");
    }
    if (showErrors && errors.size() > 0) {
      Collections.sort(errors);
      logger.log("ERRORS:");
      for (String error : errors) logger.log(error);
      logger.log("\n");
    }

    long startTime = System.currentTimeMillis();
    graph.removeImpossibleEdges();
    List<List<Graph.Vertex>> bestCycles = graph.findCycles();
    int bestMetric = metric.calculate(bestCycles);

    if (iterations > 1) {
      logger.log(metric.toString());
      graph.saveMatches();

      for (int i = 0; i < iterations-1; i++) {
        graph.shuffle();
        List<List<Graph.Vertex>> cycles = graph.findCycles();
        int newMetric = metric.calculate(cycles);

        if (newMetric < bestMetric) {
          bestMetric = newMetric;
          bestCycles = cycles;
          graph.saveMatches();
          logger.log(metric.toString());
        }
        else if (verbose)
          logger.log("# " + metric);
      }
      logger.log("\n");
      graph.restoreMatches();
    }
    long stopTime = System.currentTimeMillis();
    displayMatches(bestCycles, logger);

    if (showElapsedTime)
      logger.log("Elapsed time = " + (stopTime-startTime) + "ms");
  }

  boolean caseSensitive = false;
  boolean requireColons = false;
  boolean requireUsernames = false;
  boolean showErrors = true;
  boolean showRepeats = true;
  boolean showLoops = true;
  boolean showSummary = true;
  boolean showNonTrades = true;
  boolean showStats = true;
  boolean showMissing = false;
  boolean sortByItem = false;
  boolean allowDummies = false;
  boolean showElapsedTime = false;
  long seed = -1;

  static final int NO_PRIORITIES = 0;
  static final int LINEAR_PRIORITIES = 1;
  static final int TRIANGLE_PRIORITIES = 2;
  static final int SQUARE_PRIORITIES = 3;
  static final int SCALED_PRIORITIES = 4;
  static final int EXPLICIT_PRIORITIES = 5;

  int priorityScheme = NO_PRIORITIES;
  int smallStep = 1;
  int bigStep = 9;
  long nonTradeCost = 1000000000L; // 1 billion

  int iterations = 1;

  boolean verbose = false;
  boolean debug = false;
  
  //////////////////////////////////////////////////////////////////////
  
  List<String> options = new ArrayList<String>();
  HashSet<String> officialNames = null;
  List<String> usedNames = new ArrayList<String>();
  
  List<String[]> readWantLists(InputStream istream, FatalError err) throws IOException {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(istream));
      List<String[]> wantLists = new ArrayList<String[]>();
      boolean readingOfficialNames = false;

      for (int lineNumber = 1;;lineNumber++) {
        String line = in.readLine();
        if (line == null) return wantLists;

        line = line.trim();
        if (line.length() == 0) continue; // skip blank link
        if (line.matches("#!.*")) { // declare options
          if (wantLists.size() > 0)
            err.fatalError("Options (#!...) cannot be declared after first real want list", lineNumber);
          if (officialNames != null)
            err.fatalError("Options (#!...) cannot be declared after official names", lineNumber);
          for (String option : line.toUpperCase().substring(2).trim().split("\\s+")) {
            if (option.equals("CASE-SENSITIVE"))
              caseSensitive = true;
            else if (option.equals("REQUIRE-COLONS"))
              requireColons = true;
            else if (option.equals("REQUIRE-USERNAMES"))
              requireUsernames = true;
            else if (option.equals("HIDE-ERRORS"))
              showErrors = false;
            else if (option.equals("HIDE-REPEATS"))
              showRepeats = false;
            else if (option.equals("HIDE-LOOPS"))
              showLoops = false;
            else if (option.equals("HIDE-SUMMARY"))
              showSummary = false;
            else if (option.equals("HIDE-NONTRADES"))
              showNonTrades = false;
            else if (option.equals("HIDE-STATS"))
              showStats = false;
            else if (option.equals("SHOW-MISSING"))
              showMissing = true;
            else if (option.equals("SORT-BY-ITEM"))
              sortByItem = true;
            else if (option.equals("ALLOW-DUMMIES"))
              allowDummies = true;
            else if (option.equals("SHOW-ELAPSED-TIME"))
              showElapsedTime = true;
            else if (option.equals("LINEAR-PRIORITIES"))
              priorityScheme = LINEAR_PRIORITIES;
            else if (option.equals("TRIANGLE-PRIORITIES"))
              priorityScheme = TRIANGLE_PRIORITIES;
            else if (option.equals("SQUARE-PRIORITIES"))
              priorityScheme = SQUARE_PRIORITIES;
            else if (option.equals("SCALED-PRIORITIES"))
              priorityScheme = SCALED_PRIORITIES;
            else if (option.equals("EXPLICIT-PRIORITIES"))
              priorityScheme = EXPLICIT_PRIORITIES;
            else if (option.startsWith("SMALL-STEP=")) {
              String num = option.substring(11);
              if (!num.matches("\\d+"))
                err.fatalError("SMALL-STEP argument must be a non-negative integer",lineNumber);
              smallStep = Integer.parseInt(num);
            }
            else if (option.startsWith("BIG-STEP=")) {
              String num = option.substring(9);
              if (!num.matches("\\d+"))
                err.fatalError("BIG-STEP argument must be a non-negative integer",lineNumber);
              bigStep = Integer.parseInt(num);
            }
            else if (option.startsWith("NONTRADE-COST=")) {
              String num = option.substring(14);
              if (!num.matches("[1-9]\\d*"))
                err.fatalError("NONTRADE-COST argument must be a positive integer",lineNumber);
              nonTradeCost = Long.parseLong(num);
            }
            else if (option.startsWith("ITERATIONS=")) {
              String num = option.substring(11);
              if (!num.matches("[1-9]\\d*"))
                err.fatalError("ITERATIONS argument must be a positive integer",lineNumber);
              iterations = Integer.parseInt(num);
            }
            else if (option.startsWith("SEED=")) {
              String num = option.substring(5);
              if (!num.matches("[1-9]\\d*"))
                err.fatalError("SEED argument must be a positive integer",lineNumber);
              seed = Long.parseLong(num);
              graph.setSeed(seed);
            }
            else if (option.equals("VERBOSE"))
              verbose = true;
            else if (option.equals("DEBUG"))
              debug = true;
            else if (option.startsWith("METRIC=")) {
              String met = option.substring(7);
              if (met.matches("USERS-TRADING"))
                metric = new MetricUsersTrading();
              else if (met.matches("USERS-SOS"))
                metric = new MetricUsersSumOfSquares();
              else if (met.startsWith("FAVOR-USER=")) {
                String user = met.substring(11);
                metric = new MetricFavorUser(user);
              }
              else if (met.matches("CHAIN-SIZES-SOS")) {
                // This is the default
              }
	      else
                err.fatalError("Unknown metric option \""+met+"\"",lineNumber);
            }
            else
              err.fatalError("Unknown option \""+option+"\"",lineNumber);

            options.add(option);
          }
          continue;
        }
        if (line.matches("#.*")) continue; // skip comment line
        if (line.indexOf("#") != -1) {
          if (readingOfficialNames) {
            if (line.split("[:\\s]")[0].indexOf("#") != -1) {
              err.fatalError("# symbol cannot be used in an item name",lineNumber);
            }
          }
          else
            err.fatalError("Comments (#...) cannot be used after beginning of line",lineNumber);
        }

        // handle official names
        if (line.equalsIgnoreCase("!BEGIN-OFFICIAL-NAMES")) {
          if (officialNames != null)
            err.fatalError("Cannot begin official names more than once", lineNumber);
          if (wantLists.size() > 0)
            err.fatalError("Official names cannot be declared after first real want list", lineNumber);
            
          officialNames = new HashSet<String>();
          readingOfficialNames = true;
          continue;
        }
        if (line.equalsIgnoreCase("!END-OFFICIAL-NAMES")) {
          if (!readingOfficialNames)
            err.fatalError("!END-OFFICIAL-NAMES without matching !BEGIN-OFFICIAL-NAMES", lineNumber);
          readingOfficialNames = false;
          continue;
        }
        if (readingOfficialNames) {
          if (line.charAt(0) == ':')
            err.fatalError("Line cannot begin with colon",lineNumber);
          if (line.charAt(0) == '%')
            err.fatalError("Cannot give official names for dummy items",lineNumber);
            
          String[] toks = line.split("[:\\s]");
          String name = toks[0];
          if (!caseSensitive) name = name.toUpperCase();
          if (officialNames.contains(name))
            err.fatalError("Official name "+name+"+ already defined",lineNumber);
          officialNames.add(name);
          continue;
        }

        // check parens for user name
        if (line.indexOf("(") == -1 && requireUsernames)
          err.fatalError("Missing username with REQUIRE-USERNAMES selected",lineNumber);
        if (line.charAt(0) == '(') {
          if (line.lastIndexOf("(") > 0)
            err.fatalError("Cannot have more than one '(' per line",lineNumber);
          int close = line.indexOf(")");
          if (close == -1)
            err.fatalError("Missing ')' in username",lineNumber);
          if (close == line.length()-1)
            err.fatalError("Username cannot appear on a line by itself",lineNumber);
          if (line.lastIndexOf(")") > close)
            err.fatalError("Cannot have more than one ')' per line",lineNumber);
          if (close == 1)
            err.fatalError("Cannot have empty parentheses",lineNumber);

          // temporarily replace spaces in username with #'s
          if (line.indexOf(" ") < close) {
            line = line.substring(0,close+1).replaceAll(" ","#")+" "
                    + line.substring(close+1);
          }
        }
        else if (line.indexOf("(") > 0)
          err.fatalError("Username can only be used at the front of a want list",lineNumber);
        else if (line.indexOf(")") > 0)
          err.fatalError("Bad ')' on a line that does not have a '('",lineNumber);

          
        // check semicolons
        line = line.replaceAll(";"," ; ");
        int semiPos = line.indexOf(";");
        if (semiPos != -1) {
          if (semiPos < line.indexOf(":"))
            err.fatalError("Semicolon cannot appear before colon",lineNumber);
          String before = line.substring(0,semiPos).trim();
          if (before.length() == 0 || before.charAt(before.length()-1) == ')')
            err.fatalError("Semicolon cannot appear before first item on line", lineNumber);
        }
        
        // check and remove colon
        int colonPos = line.indexOf(":");
        if (colonPos != -1) {
          if (line.lastIndexOf(":") != colonPos)
            err.fatalError("Cannot have more that one colon on a line",lineNumber);
          String header = line.substring(0,colonPos).trim();
          if (!header.matches("(.*\\)\\s+)?[^(\\s)]\\S*"))
            err.fatalError("Must have exactly one item before a colon (:)",lineNumber);
          line = line.replaceFirst(":"," "); // remove colon
        }
        else if (requireColons) {
          err.fatalError("Missing colon with REQUIRE-COLONS selected",lineNumber);
        }

        if (!caseSensitive) line = line.toUpperCase();
        wantLists.add(line.trim().split("\\s+"));
      }
    }
    catch(Exception e) {
      err.fatalError(e.getMessage());
      return null;
    }
  }

  String parseArgs(String[] args, boolean doit, FatalError err, Logger logger) throws IOException {
    int c, optind;
    LongOpt[] longopts = new LongOpt[22];

    longopts[0] = new LongOpt("help",
        LongOpt.NO_ARGUMENT, null, 'h');
    longopts[1] = new LongOpt("allow-dummies",
        LongOpt.OPTIONAL_ARGUMENT, null, 'd');
    longopts[2] = new LongOpt("require-colons",
        LongOpt.OPTIONAL_ARGUMENT, null, 'c');
    longopts[3] = new LongOpt("require-usernames",
        LongOpt.OPTIONAL_ARGUMENT, null, 'u');
    longopts[4] = new LongOpt("hide-loops",
        LongOpt.OPTIONAL_ARGUMENT, null, 'l');
    longopts[5] = new LongOpt("hide-summary",
        LongOpt.OPTIONAL_ARGUMENT, null, 's');
    longopts[6] = new LongOpt("hide-nontrades",
        LongOpt.OPTIONAL_ARGUMENT, null, 'n');
    longopts[7] = new LongOpt("hide-errors",
        LongOpt.OPTIONAL_ARGUMENT, null, 'e');
    longopts[8] = new LongOpt("hide-stats",
        LongOpt.OPTIONAL_ARGUMENT, null, 't');
    longopts[9] = new LongOpt("hide-repeats",
        LongOpt.OPTIONAL_ARGUMENT, null, 'r');
    longopts[10] = new LongOpt("case-sensitive",
        LongOpt.OPTIONAL_ARGUMENT, null, 'C');
    longopts[11] = new LongOpt("sort-by-item",
        LongOpt.OPTIONAL_ARGUMENT, null, 'i');
    longopts[12] = new LongOpt("small-step",
        LongOpt.REQUIRED_ARGUMENT, null, 'm');
    longopts[13] = new LongOpt("big-step",
        LongOpt.REQUIRED_ARGUMENT, null, 'b');
    longopts[14] = new LongOpt("nontrade-cost",
        LongOpt.REQUIRED_ARGUMENT, null, 'N');
    longopts[15] = new LongOpt("seed",
        LongOpt.REQUIRED_ARGUMENT, null, 'S');
    longopts[16] = new LongOpt("iterations",
        LongOpt.REQUIRED_ARGUMENT, null, 'I');
    longopts[17] = new LongOpt("priorities",
        LongOpt.OPTIONAL_ARGUMENT, null, 'p');
    longopts[18] = new LongOpt("show-missing",
        LongOpt.OPTIONAL_ARGUMENT, null, 'G');
    longopts[19] = new LongOpt("show-elapsed-time",
        LongOpt.OPTIONAL_ARGUMENT, null, 'T');
    longopts[20] = new LongOpt("metric",
        LongOpt.REQUIRED_ARGUMENT, null, 'M');
    longopts[21] = new LongOpt("verbose",
        LongOpt.NO_ARGUMENT, null, 'v');

    Getopt g = new Getopt("TradeMaximizer", args,
        "hdculsnetrCim:b:N:S:I:p:GTM:v", longopts);

    while( (c = g.getopt()) != -1 ) {
      String arg = g.getOptarg();
      boolean bool = true;
      if (arg != null && (arg.equalsIgnoreCase("false")
                           || arg.equalsIgnoreCase("off") || arg.equals("0")))
        bool = false;

      if (doit) switch( c ) {
        case 'h' :
          System.err.println("TradeMaximizer " + version + "\n" +
"Please see http://www.boardgamegeek.com/wiki/page/TradeMaximizer for details\n"
+ "on each option.  For binary style options optional argument can be\n" +
"'false', 'off' or '0', anything else ia sssumed true/on");

          int i;
          for (i = 0 ; i < longopts.length ; i++) {
            logger.log("    -" + (char)longopts[i].getVal()
              + " --" + longopts[i].getName());
          }
          break;
        case 'd' : allowDummies = bool; break;
        case 'c' : requireColons = bool; break;
        case 'u' : requireUsernames = bool; break;
        case 'l' : showLoops = ! bool; break;
        case 's' : showSummary = ! bool; break;
        case 'n' : showNonTrades = ! bool; break;
        case 'e' : showErrors = ! bool; break;
        case 't' : showStats = ! bool; break;
        case 'r' : showRepeats = ! bool; break;
        case 'C' : caseSensitive = bool; break;
        case 'i' : sortByItem = bool; break;
        case 'm' : smallStep = Integer.parseInt(arg); break;
        case 'b' : bigStep = Integer.parseInt(arg); break;
        case 'N' : nonTradeCost = Long.parseLong(arg); break;
        case 'I' : iterations = Integer.parseInt(arg); break;
        case 'G' : showMissing = bool; break;
        case 'v' : verbose = bool; break;
        case 'T' : showElapsedTime = bool; break;
        case 'S' :
          seed = Long.parseLong(arg);
          graph.setSeed(seed);
          break;
        case 'p' :
          if( arg == null ) arg = "linear";

          if( arg.equalsIgnoreCase("linear") )
            priorityScheme = LINEAR_PRIORITIES;
          else if( arg.equalsIgnoreCase("triangle") )
            priorityScheme = TRIANGLE_PRIORITIES;
          else if( arg.equalsIgnoreCase("square") )
            priorityScheme = SQUARE_PRIORITIES;
          else if( arg.equalsIgnoreCase("scaled") )
            priorityScheme = SCALED_PRIORITIES;
          else if( arg.equalsIgnoreCase("explicit") )
            priorityScheme = EXPLICIT_PRIORITIES;
          else if( arg.equalsIgnoreCase("none") )
            priorityScheme = NO_PRIORITIES;
	  else
            err.fatalError("Unknown priority type: " + arg);
          break;
        case 'M' :
          String met = arg.toUpperCase();
          if (met.matches("USERS-TRADING"))
            metric = new MetricUsersTrading();
          else if (met.matches("USERS-SOS"))
            metric = new MetricUsersSumOfSquares();
          else if (met.startsWith("FAVOR-USER=")) {
            String user = met.substring(11);
            metric = new MetricFavorUser(user);
          }
          else if (met.matches("CHAIN-SIZES-SOS"))
            metric = new MetricSumSquares();
          else
            err.fatalError("Unknown metric: " + met);
          break;
        case '?' :
	  err.fatalError("Exiting due to unknown or badly form command line option");
          break;
        default :
          break;
      } // switch
    } // while

    optind = g.getOptind();

    return optind < args.length ? args[optind] : null;
  }


  class FatalError {

    private OutputStream outputStream;

    public FatalError(OutputStream outputStream) {
      this.outputStream = outputStream;
    }

    void fatalError(String msg) throws IOException {
        outputStream.write("\n".getBytes());
      outputStream.write(("FATAL ERROR: " + msg).getBytes());
    }

    void fatalError(String msg,int lineNumber) throws IOException {
      fatalError(msg + " (line " + lineNumber + ")");
    }
  }

  //////////////////////////////////////////////////////////////////////

  Graph graph = new Graph();

  List< String > errors = new ArrayList< String >();

  final long INFINITY = 100000000000000L; // 10^14
  // final long NOTRADE  = 1000000000L; // replaced by nonTradeCost
  final long UNIT     = 1L;

  int ITEMS; // the number of items being traded (including dummy items)
  int DUMMY_ITEMS; // the number of dummy items

  String[] deleteFirst(String[] a) {
    assert a.length > 0;
    String[] b = new String[a.length-1];
    for (int i = 0; i < b.length; i++) b[i] = a[i+1];
    return b;
  }
  
  void buildGraph(List< String[] > wantLists) {

    HashMap< String,Integer > unknowns = new HashMap< String,Integer >();
    
    // create the nodes
    for (int i = 0; i < wantLists.size(); i++) {
      String[] list = wantLists.get(i);
      assert list.length > 0;
      String name = list[0];
      String user = null;
      int offset = 0;
      if (name.charAt(0) == '(') {
        user = name.replaceAll("#"," "); // restore spaces in username
        // remove username from list
        list = deleteFirst(list);
          // was Arrays.copyOfRange(list,1,list.length);
          // but that caused problems on Macs
        wantLists.set(i,list);
        name = list[0];
      }
      boolean isDummy = (name.charAt(0) == '%');
      if (isDummy) {
        if (user == null)
          errors.add("**** Dummy item " + name + " declared without a username.");
        else if (!allowDummies)
          errors.add("**** Dummy items not allowed. ("+name+")");
        else {
          name += " for user " + user;
          list[0] = name;
        }
      }
      if (officialNames != null && !officialNames.contains(name) && name.charAt(0) != '%') {
        errors.add("**** Cannot define want list for "+name+" because it is not an official name.  (Usually indicates a typo by the item owner.)");
        wantLists.set(i,null);
      }
      else if (graph.getVertex(name) != null) {
        errors.add("**** Item " + name + " has multiple want lists--ignoring all but first.  (Sometimes the result of an accidental line break in the middle of a want list.)");
        wantLists.set(i, null);
      }
      else {
        ITEMS++;
        if (isDummy) DUMMY_ITEMS++;
        Graph.Vertex vertex = graph.addVertex(name,user,isDummy);
        if (officialNames != null && officialNames.contains(name))
          usedNames.add(name);
        
        if (!isDummy) width = Math.max(width, show(vertex).length());
      }
    }

    // create the edges
    for (String[] list : wantLists) {
      if (list == null) continue; // skip the duplicate lists
      String fromName = list[0];
      Graph.Vertex fromVertex = graph.getVertex(fromName);

      // add the "no-trade" edge to itself
      graph.addEdge(fromVertex,fromVertex.twin,nonTradeCost);

      long rank = 1;
      for (int i = 1; i < list.length; i++) {
        String toName = list[i];
        if (toName.equals(";")) {
          rank += bigStep;
          continue;
        }
        if (toName.indexOf('=') >= 0) {
          if (priorityScheme != EXPLICIT_PRIORITIES) {
            errors.add("**** Cannot use '=' annotation in item "+toName+" in want list for item "+fromName+" unless using EXPLICIT_PRIORITIES.");
            continue;
          }
          if (!toName.matches("[^=]+=[0-9]+")) {
            errors.add("**** Item "+toName+" in want list for item "+fromName+" must have the format 'name=number'.");
            continue;
          }
          String[] parts = toName.split("=");
          assert(parts.length == 2);
          long explicitCost = Long.parseLong(parts[1]);
          if (explicitCost < 1) {
            errors.add("**** Explicit priority must be positive in item "+toName+" in want list for item "+fromName+".");
            continue;
          }
          rank = explicitCost;
          toName = parts[0];
        }
        if (toName.charAt(0) == '%') {
          if (fromVertex.user == null) {
            errors.add("**** Dummy item " + toName + " used in want list for item " + fromName + ", which does not have a username.");
            continue;
          }

          toName += " for user " + fromVertex.user; 
        }
        Graph.Vertex toVertex = graph.getVertex(toName);
        if (toVertex == null) {
          if (officialNames != null && officialNames.contains(toName)) {
            // this is an official item whose owner did not submit a want list
            rank += smallStep;            
          }
          else {
            int occurrences = unknowns.containsKey(toName) ? unknowns.get(toName) : 0;
            unknowns.put(toName,occurrences + 1);
          }
          continue;
        }
        
        toVertex = toVertex.twin; // adjust to the sending vertex
        if (toVertex == fromVertex.twin) {
          errors.add("**** Item " + toName + " appears in its own want list.");
        }
        else if (graph.getEdge(fromVertex,toVertex) != null) {
          if (showRepeats)
            errors.add("**** Item " + toName + " is repeated in want list for " + fromName + ".");
        }
        else if (!toVertex.isDummy &&
                 fromVertex.user != null &&
                 fromVertex.user.equals(toVertex.user)) {
          errors.add("**** Item "+fromVertex.name +" contains item "+toVertex.name+" from the same user ("+fromVertex.user+")");
        }
        else {
          long cost = UNIT;
          switch (priorityScheme) {
            case LINEAR_PRIORITIES:   cost = rank; break;
            case TRIANGLE_PRIORITIES: cost = rank*(rank+1)/2; break;
            case SQUARE_PRIORITIES:   cost = rank*rank; break;
            case SCALED_PRIORITIES:   cost = rank; break; // assign later
            case EXPLICIT_PRIORITIES: cost = rank; break;
          }

          // all edges out of a dummy node have the same cost
          if (fromVertex.isDummy) cost = nonTradeCost;

          graph.addEdge(fromVertex,toVertex,cost);

          rank += smallStep;
        }
      }

      // update costs for those priority schemes that need information such as
      // number of wants
      if (!fromVertex.isDummy) {
        switch (priorityScheme) {
          case SCALED_PRIORITIES:
            int n = fromVertex.edges.size()-1;
            for (Graph.Edge edge : fromVertex.edges) {
              if (edge.sender != fromVertex.twin)
                edge.cost = 1 + (edge.cost-1)*2520/n;
            }
            break;
        }
      }
    }

    graph.freeze();

    for (Map.Entry< String,Integer > entry : unknowns.entrySet()) {
      String item = entry.getKey();
      int occurrences = entry.getValue();
      String plural = occurrences == 1 ? "" : "s";
      errors.add("**** Unknown item " + item + " (" + occurrences + " occurrence" + plural + ")");
    }

  } // end buildGraph

  String show(Graph.Vertex vertex) {
    if (vertex.user == null || vertex.isDummy) return vertex.name;
    else if (sortByItem) return vertex.name + " " + vertex.user;
    else return vertex.user + " " + vertex.name;
  }

  //////////////////////////////////////////////////////////////////////
  
  void displayMatches(List<List<Graph.Vertex>> cycles, Logger logger) throws IOException {
    int numTrades = 0;
    int numGroups = cycles.size();
    int totalCost = 0;
    int sumOfSquares = 0;
    List< Integer > groupSizes = new ArrayList< Integer >();

    List< String > summary = new ArrayList< String >();
    List< String > loops = new ArrayList< String >();

    for (List<Graph.Vertex> cycle : cycles) {
      int size = cycle.size();
      numTrades += size;
      sumOfSquares += size*size;
      groupSizes.add(size);
      for (Graph.Vertex v : cycle) {
        assert v.match != v.twin;
        loops.add(pad(show(v)) + " receives " + show(v.match.twin));
        summary.add(pad(show(v)) + " receives " + pad(show(v.match.twin)) + " and sends to " + show(v.twin.match));
        totalCost += v.matchCost;
      }
      loops.add("");
    }
    if (showNonTrades) {
      for (Graph.Vertex v : graph.RECEIVERS) {
        if (v.match == v.twin && !v.isDummy)
          summary.add(pad(show(v)) + "             does not trade");
      }
      for (Graph.Vertex v : graph.orphans) {
        if (!v.isDummy)
          summary.add(pad(show(v)) + "             does not trade");
      }
    }

    if (showLoops) {
      logger.log("TRADE LOOPS (" + numTrades + " total trades):");
      logger.log();
      for (String item : loops) logger.log(item);
    }
    
    if (showSummary) {
      Collections.sort(summary);
      logger.log("ITEM SUMMARY (" + numTrades + " total trades):");
      logger.log();
      for (String item : summary) logger.log(item);
      logger.log();
    }

    
    System.out.print("Num trades  = " + numTrades + " of " + (ITEMS-DUMMY_ITEMS) + " items");    
    if (ITEMS-DUMMY_ITEMS == 0) logger.log();
    else logger.log(new DecimalFormat(" (0.0%)").format(numTrades/(double)(ITEMS-DUMMY_ITEMS)));
    
    if (showStats) {
      System.out.print("Total cost  = " + totalCost);
      if (numTrades == 0) logger.log();
      else logger.log(new DecimalFormat(" (avg 0.00)").format(totalCost/(double)numTrades));
      logger.log("Num groups  = " + numGroups);
      System.out.print("Group sizes =");
      Collections.sort(groupSizes);
      Collections.reverse(groupSizes);
      for (int groupSize : groupSizes) System.out.print(" " + groupSize);
      logger.log();
      logger.log("Sum squares = " + sumOfSquares);

//      logger.log("Orphans     = " + graph.orphans.size());
    }
  }

  int width = 1;
  String pad(String name) {
    while (name.length() < width) name += " ";
    return name;
  }

  String toHexString(byte[] bytes) {
    String str = new String();

    for (byte b : bytes) str = str + Integer.toHexString(b & 0xff);

    return str;
  }

  class Logger {
    private OutputStream outputStream;
    public Logger(OutputStream outputStream) {
      this.outputStream = outputStream;
    }
    public void log() throws IOException {
        outputStream.write("\n".getBytes());
    }
    public void log(String msg) {
      try {
        outputStream.write(msg.getBytes());
        outputStream.write("\n".getBytes());
      } catch (IOException e) {
          // todo: do something about it
      }
    }
  }

} // end TradeMaximizer
