import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.io.*;

/**
 * @author parker
 *
 */
public class Simulator {
  private static int                 PIPELINE_DEPTH = 8;
  private Map<Integer, Integer>      registerFile;
  private Map<Integer, Integer>      mainMemory;
  private List<Instruction>          instructionMemory;
  private LinkedList<PipelineBuffer> bufferList;
  private int                        pc;
  private int                        cc;
  private PipelineBuffer             curBuffer;
  private boolean                    endOfInstructionMem;
  private boolean                    finishedLastCycle;
  private boolean                    stalling;

  public Simulator(String inputPath) {
    InputParser parser = new InputParser(inputPath);
    try {
      parser.parseFile();
    } catch (Exception e) {
      System.out.println(e.toString());
    }
    registerFile = parser.getRegisterFile();
    mainMemory = parser.getMainMemory();
    instructionMemory = parser.getInstructionMemory();
    bufferList = new LinkedList<PipelineBuffer>();
    endOfInstructionMem = false;
    finishedLastCycle = false;
    stalling = false;
    pc = 0;
    cc = 1;
  }

  private void WB() {
    if ((curBuffer = getBuffer(8)) != null) {
      System.out.print(" I" + curBuffer.instructNum + "-WB");
      bufferList.removeLast();
      if (curBuffer.memToReg == 1) {
        registerFile.put(curBuffer.writeReg, curBuffer.memData);
      } else if (curBuffer.memToReg == 0) {
        registerFile.put(curBuffer.writeReg, curBuffer.aluResult);
      }
    } else if (bufferList.size() >= 7) {
      bufferList.removeLast();
    } else {
      finishedLastCycle = endOfInstructionMem ? true : false;
    }
  }

  private void MEM3() {
    if ((curBuffer = getBuffer(7)) != null) {
      System.out.print(" I" + curBuffer.instructNum + "-MEM3");
      // NOP
    }
  }

  private void MEM2() {
    if ((curBuffer = getBuffer(6)) != null) {
      System.out.print(" I" + curBuffer.instructNum + "-MEM2");
      // NOP
    }
  }

  private void MEM1() {
    if ((curBuffer = getBuffer(5)) != null) {
      System.out.print(" I" + curBuffer.instructNum + "-MEM1");
      int address = curBuffer.aluResult;
      if (curBuffer.memRead == 1) {
        int readData = mainMemory.get(address);
        curBuffer.memData = readData;
      } else if (curBuffer.memWrite == 1) {
        int writeData = curBuffer.readData2;
        mainMemory.put(address, writeData);
      }
    }
  }

  private void EX() {
    if ((curBuffer = getBuffer(4)) != null) {
      System.out.print(" I" + curBuffer.instructNum + "-EX");
      int operand1 = curBuffer.readData1;
      int operand2;
      if (curBuffer.aluSrc == 1) {
        operand2 = curBuffer.readData2;
      } else {
        operand2 = curBuffer.immediate;
      }

      switch (curBuffer.opcode) {
      case DADD:
        curBuffer.aluResult = operand1 + operand2;
        break;
      case SUB:
        curBuffer.aluResult = operand1 - operand2;
        break;
      case LD:
        curBuffer.aluResult = operand1 + operand2;
        break;
      case SD:
        curBuffer.aluResult = operand1 + operand2;
        break;
      case BNEZ:
        curBuffer.zero = (operand1 == 0) ? true : false;
        break;
      default:
        System.out.println("NOT VALID OPCODE");
      }
    }
  }

  /**
   * Instruction Decode.
   *
   * Decodes the instruction in the pipeline buffer.
   * Calculate the target address of a Branch.
   * Check if stall is required.
   */
  private void ID() {
    if ((curBuffer = getBuffer(3)) != null) {
      System.out.print(" I" + curBuffer.instructNum);
      Instruction curInstr = curBuffer.instr;
      boolean getData1 = false, getData2 = false;
      // Set control bits based on op code
      curBuffer.setControlSignals();
      // Decode read1
      if (curInstr.rs != null) {
        // If Source is a register
        if (curInstr.rs.charAt(0) == 'R') {
          int regNum = Integer.valueOf(curInstr.rs.substring(1));
          curBuffer.readReg1 = regNum;
          getData1 = true;
        } else {
          System.out.println(curInstr.rd + " is not a register!");
        }
      }
      // Decode read2 if rt not the destination register
      if (curInstr.rt != null) {
        // If Source is a register
        if (curInstr.rt.charAt(0) == 'R') {
          int regNum = Integer.valueOf(curInstr.rt.substring(1));
          curBuffer.readReg2 = regNum;
          if (curBuffer.memRead != 1) {
            getData2 = true;
          }
        } else if (curInstr.rt.charAt(0) == '#') {
          curBuffer.immediate = Integer.valueOf(curInstr.rt.substring(1));
          curBuffer.aluSrc = 0;
        } else {
          System.out.println(curInstr.rd + " is not a register!");
        }
      }
      // Decode writeReg
      if (curBuffer.regDst == 0 && curInstr.rd != null) {
        curBuffer.writeReg = Integer.valueOf(curInstr.rd.substring(1)).intValue();
      } else if (curBuffer.regDst == 1 && curInstr.rt != null) {
        curBuffer.writeReg = Integer.valueOf(curInstr.rt.substring(1));
      }
      // Decode immediate
      if (curInstr.immediate != null) {
        curBuffer.immediate = curInstr.immediate;
        // Calculate the address of next instruction for BNEZ
        curBuffer.branchAddr = curBuffer.curPC + curBuffer.immediate;
      }
      // Check if a stall is required
      if (shouldStall()) {
        stalling = true;
        System.out.print("-stall");
        bufferList.add(2, null);
      } else {
        // Read register data
        if (getData1) {
          curBuffer.readData1 = getReadData(curBuffer.readReg1);
        }
        if (getData2) {
          curBuffer.readData2 = getReadData(curBuffer.readReg2);
        }
        System.out.print("-ID");
      }
    }
  }

  /**
   * Instruction Fetch 2.
   * Does nothing
   */
  private void IF2() {
    if ((curBuffer = getBuffer(2)) != null) {
      if (stalling) {
        System.out.print(" I" + curBuffer.instructNum + "-stall");
      } else {
        System.out.print(" I" + curBuffer.instructNum + "-IF2");
        // NOP
      }
    }
  }

  /**
   * Instruction Fetch 1.
   * 
   * Updates the program counter if branch taken and kills existing instructions.
   * Gets the next instruction from {@link instructionMemory} and creates the
   * PipelineBuffer. Advances the program counter to the next value.
   */
  private void IF1() {
    updatePC();
    Instruction curInstruct = (pc < instructionMemory.size()) ? instructionMemory.get(pc) : null;
    int instrNum = pc + 1;
    if (curInstruct != null) {
      if (!stalling) {
        System.out.print(" I" + instrNum + "-IF1");
        curBuffer = new PipelineBuffer(curInstruct, instrNum);
        curBuffer.curPC = pc;
        pc += 1; // Increment PC
        bufferList.addFirst(curBuffer);
      } else {
        System.out.print(" I" + instrNum + "-stall");
      }
    } else if (!stalling) {
      bufferList.addFirst(null);
      endOfInstructionMem = true;
    }
    stalling = false;
  }

  /**
   * Sets PC to the value of ex.branchAddr and kills instructions if branch is taken. Does nothing if branch not taken. 
   */
  private void updatePC() {
    PipelineBuffer exBuff = getBuffer(4);
    if (exBuff != null && exBuff.branchTaken()) {
      killBadInstructions();
      pc = exBuff.branchAddr;
    }
  }

  /**
   * Kills instructions in the IF2 and ID stages.
   */
  private void killBadInstructions() {
    bufferList.set(0, null);
    bufferList.set(1, null);
  }

  /**
   * Returns the {@link PipelineBuffer} for a stage in the pipeline.
   */
  private PipelineBuffer getBuffer(int stageNum) {
    if (bufferList.size() >= stageNum - 1) {
      return bufferList.get(stageNum - 2);
    } else {
      return null;
    }
  }

  /**
   * Gets data for a given regsiter in the ID stage.
   * Handles forwards from EX and MEM.
   * Otherwise reads from register file.
   */
  private Integer getReadData(int regNum) {
    Integer data = null;
    // For every instruction in EX through WB
    for (int i = PIPELINE_DEPTH - 1; i > 3; i--) {
      PipelineBuffer forwardBuff = getBuffer(i);
      // If instruct will write into one of id's operands
      if (forwardBuff != null && forwardBuff.regWrite == 1 && forwardBuff.writeReg == regNum) {
        switch (forwardBuff.opcode) {
        case DADD:
          // Value from EX stage
          data = (i > 3) ? forwardBuff.aluResult : null;
          break;
        case SUB:
          // Value from EX stage
          data = (i > 3) ? forwardBuff.aluResult : null;
          break;
        case LD:
          // Value from MEM2 stage
          data = (i > 5) ? forwardBuff.memData : null;
          break;
        default:
          break;
        }
      }
    }
    if (data != null) {
      return data;
    } else {
      return registerFile.get(regNum);
    }
  }

  /**
   * Determines if stall is required in the ID stage.
   * Stall if either rd.readReg is the writeReg of a instruction ahead of it in the pipeline.
   */
  private boolean shouldStall() {
    boolean stall = false;
    PipelineBuffer idBuff = getBuffer(3);
    // For every instruction in EX through WB
    for (int i = 4; i < 6; i++) {
      PipelineBuffer forwardBuff = getBuffer(i);
      if (forwardBuff != null) {
        if (idBuff.readReg1 == forwardBuff.writeReg && forwardBuff.opcode == Instruction.Opcode.LD) {
          stall = true;
        } else if (idBuff.readReg2 == forwardBuff.writeReg
            && forwardBuff.opcode == Instruction.Opcode.LD) {
          stall = true;
        }
      }
    }
    return stall;
  }

  /**
   * Determines if simulation loop should continue.
   * Returns false if all buffers in list are null.
   */
  private boolean keepGoing() {
    if (bufferList.size() < 7) {
      return true;
    } else {
      boolean result = false;
      for (PipelineBuffer buffer : bufferList) {
        if (buffer != null)
          result = true;
      }
      return result;
    }
  }

  public void printResults() {
    System.out.println("REGISTERS");
    for (Integer regNum : registerFile.keySet()) {
      System.out.println("R" + regNum + " " + registerFile.get(regNum));
    }
    System.out.println("MEMORY");
    for (Integer address : mainMemory.keySet()) {
      System.out.println(address + " " + mainMemory.get(address));
    }
  }

  public void runSimulation() {
    while (keepGoing()) {
      System.out.print("c#" + cc);
      WB();
      MEM3();
      MEM2();
      MEM1();
      EX();
      ID();
      IF2();
      IF1();
      cc++;
      System.out.println();
    }
    printResults();
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    String inputPath = null;
    if (args.length > 0) {
      inputPath = args[0];
    } else {
      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
      System.out.println("MIPS Simulator");
      System.out.print("Input file path: ");
      try {
         inputPath = reader.readLine();
      } catch (IOException ioe) {
         System.out.println("IO error reading input path!");
         System.exit(1);
      }
      String outputPath = null;
      System.out.print("Output file path: ");
      try {
         outputPath = reader.readLine();
      } catch (IOException ioe) {
         System.out.println("IO error reading output path!");
         System.exit(1);
      }
    }
    Simulator sim = new Simulator(inputPath);
    sim.runSimulation();
  }
}