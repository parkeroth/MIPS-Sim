import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InputParser {
  private static Logger          logger = Logger.getLogger(InputParser.class.getName());
  private BufferedReader         reader;
  private Map<Integer, Integer> registerFile;
  private Map<Integer, Integer>  mainMemory;
  private Map<String, Integer>   targetMap;
  private List<Instruction>      instructionMemory;
  private List<String>           instructionList;
  private int                    numInstructions;

  public InputParser(String inputPath) {
    try {
      reader = new BufferedReader(new FileReader(inputPath));
    } catch (FileNotFoundException e) {
      logger.log(Level.SEVERE, "Couldn't open file: " + inputPath);
      System.exit(1);
    }
    registerFile = new HashMap<Integer, Integer>();
    mainMemory = new TreeMap<Integer, Integer>();
    targetMap = new HashMap<String, Integer>();
    instructionMemory = new ArrayList<Instruction>();
    instructionList = new ArrayList<String>();
    numInstructions = 0;
  }

  public void parseFile() throws IOException {
    String line = reader.readLine();
    if (!line.contains("REGISTERS")) {
      logger.log(Level.SEVERE, "File has no REGISTER section.");
      throw new IOException("No register section!");
    } else {
      line = reader.readLine();
    }
    // Load all initial register values
    while (!line.contains("MEMORY")) {
      loadRegisterValue(line);
      line = reader.readLine();
    }
    if (!line.contains("MEMORY")) {
      logger.log(Level.SEVERE, "File has no MEMORY section.");
      throw new IOException("No memory section!");
    } else {
      line = reader.readLine();
    }
    // Load all initial memory values
    while (!line.contains("CODE")) {
      loadMemoryValue(line);
      line = reader.readLine();
    }
    if (!line.contains("CODE")) {
      logger.log(Level.SEVERE, "File has no CODE section.");
      throw new IOException("No code section!");
    }
    // Process all instructions
    while ((line = reader.readLine()) != null) {
      loadInstruction(line);
    }
    convertJumpTargets();
  }

  private void convertJumpTargets() {
    for (int i=0; i < instructionList.size(); i++) {
      Instruction instr = new Instruction(instructionList.get(i), i, targetMap);
      instructionMemory.add(instr);
    }
  }

  private void loadRegisterValue(String line) {
    String[] tokens = line.split(" ");
    int registerNumber = Integer.parseInt(tokens[0].substring(1));
    int value = Integer.parseInt(tokens[1]);
    registerFile.put(registerNumber, value);
  }

  private void loadMemoryValue(String line) {
    String[] tokens = line.split(" ");
    int address = Integer.parseInt(tokens[0]);
    int value = Integer.parseInt(tokens[1]);
    mainMemory.put(address, value);
  }

  private void loadInstruction(String line) {
    // Get label and trim until OPCODE
    String label = null;
    int colonLoc = line.indexOf(":");
    if (colonLoc > -1) {
      label = line.substring(0, colonLoc);
      line = line.substring(colonLoc + 2, line.length());
    } else {
      line = line.trim();
    }
    
    if (label != null) {
      targetMap.put(label, numInstructions);
    }
    instructionList.add(line);
    numInstructions++;
  }

  public Map<Integer, Integer> getRegisterFile() {
    return registerFile;
  }

  public Map<Integer, Integer> getMainMemory() {
    return mainMemory;
  }

  public List<Instruction> getInstructionMemory() {
    return instructionMemory;
  }
}
