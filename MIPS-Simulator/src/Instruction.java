import java.util.Map;

public class Instruction {
  public Opcode function;
  public String rs;
  public String rt;
  public String rd;
  public String shamt;
  public Integer immediate;
  public Integer target;

  public enum Opcode {
    DADD, SUB, LD, SD, BNEZ
  }
  
  public Instruction(String str, int instructionNum, Map<String, Integer> targetMap) {
    target = null;
    shamt = null;
    String[] tokens = str.split("\\s*(,|\\s)\\s*");
    String opStr = tokens[0];
    // Parse Opcode
    if (opStr.equals("DADD")) {
      function = Opcode.DADD;
    } else if (opStr.equals("SUB")) {
      function = Opcode.SUB;
    } else if (opStr.equals("LD")) {
      function = Opcode.LD;
    } else if (opStr.equals("SD")) {
      function = Opcode.SD;
    } else if (opStr.equals("BNEZ")) {
      function = Opcode.BNEZ;
    }
    
    // Set values of rs/rt/rd...
    if (function == Opcode.DADD || function == Opcode.SUB) {
      rd = tokens[1];
      rs = tokens[2];
      rt = tokens[3];
    } else if (function == Opcode.LD || function == Opcode.SD) {
      rt = tokens[1];
      int parenLoc = tokens[2].indexOf("(");
      immediate = Integer.decode(tokens[2].substring(0, parenLoc));
      rs = tokens[2].substring(parenLoc + 1, tokens[2].length() - 1);
      rd = null;
    } else if (function == Opcode.BNEZ) {
      rs = tokens[1];
      rt = null;
      rd = null;
      String targetStr = tokens[2];
      immediate = targetMap.get(targetStr) - instructionNum;
    } else {
      throw new UnsupportedOperationException("Funciton: " + function + " not supported");
    }
  }
}
