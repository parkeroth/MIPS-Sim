import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.io.*;

/**
 * @author parker
 *
 */
public class Simulator {
  private BufferedWriter             outputWriter;
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
  private boolean                    killing;
  private int                        instructionsFetched;

  public Simulator(String inputPath, String outputPath) {
    InputParser parser = new InputParser(inputPath);
    try {
      outputWriter = new BufferedWriter(new FileWriter(outputPath));
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
    killing = false;
    pc = 0;
    cc = 1;
    instructionsFetched = 0;
  }
  
  private void writeOutput(String str, boolean newLine) {
    try {
      if (str != null) {
        outputWriter.write(str);
        System.out.print(str);
      }
      if (newLine) {
        outputWriter.newLine();
        System.out.println();
      }
    } catch (IOException e) {
      System.out.println("ERROR: writing to file" + e.toString());
    }
  }
  
  private void writeOutput(String str){
    writeOutput(str, false);
  }

  private void WB() {
    if ((curBuffer = getBuffer(8)) != null) {
      writeOutput(" I" + curBuffer.instructNum + "-WB");
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
      writeOutput(" I" + curBuffer.instructNum + "-MEM3");
      // NOP
    }
  }

  private void MEM2() {
    if ((curBuffer = getBuffer(6)) != null) {
      writeOutput(" I" + curBuffer.instructNum + "-MEM2");
      // NOP
    }
  }

  private void MEM1() {
    if ((curBuffer = getBuffer(5)) != null) {
      writeOutput(" I" + curBuffer.instructNum + "-MEM1");
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
      // Check if a stall is required
      writeOutput(" I" + curBuffer.instructNum);
      if (shouldStall()) {
        stalling = true;
        writeOutput("-stall");
        bufferList.add(3, null);
      } else {
        writeOutput("-EX");
        // Read register data
        if (curBuffer.getData1) {
          curBuffer.readData1 = getReadData(curBuffer.readReg1);
        }
        if (curBuffer.getData2) {
          curBuffer.readData2 = getReadData(curBuffer.readReg2);
        }
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
          writeOutput("NOT VALID OPCODE");
        }
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
      writeOutput(" I" + curBuffer.instructNum);
      if (!stalling) {
        Instruction curInstr = curBuffer.instr;
        // Set control bits based on op code
        curBuffer.setControlSignals();
        // Decode read1
        if (curInstr.rs != null) {
          // If Source is a register
          if (curInstr.rs.charAt(0) == 'R') {
            int regNum = Integer.valueOf(curInstr.rs.substring(1));
            curBuffer.readReg1 = regNum;
            curBuffer.getData1 = true;
          } else {
            writeOutput(curInstr.rd + " is not a register!");
          }
        }
        // Decode read2 if rt not the destination register
        if (curInstr.rt != null) {
          // If Source is a register
          if (curInstr.rt.charAt(0) == 'R') {
            int regNum = Integer.valueOf(curInstr.rt.substring(1));
            curBuffer.readReg2 = regNum;
            if (curBuffer.memRead != 1) {
              curBuffer.getData2 = true;
            }
          } else if (curInstr.rt.charAt(0) == '#') {
            curBuffer.immediate = Integer.valueOf(curInstr.rt.substring(1));
            curBuffer.aluSrc = 0;
          } else {
            writeOutput(curInstr.rd + " is not a register!");
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
        writeOutput("-ID");
      } else {
        writeOutput("-stall");
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
        writeOutput(" I" + curBuffer.instructNum + "-stall");
      } else {
        writeOutput(" I" + curBuffer.instructNum + "-IF2");
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
    int instrNum = instructionsFetched + 1;
    Instruction curInstruct = (pc < instructionMemory.size()) ? instructionMemory.get(pc) : null;
    if (curInstruct != null && !killing && !stalling) {
      if (!stalling) {
        writeOutput(" I" + instrNum + "-IF1");
        curBuffer = new PipelineBuffer(curInstruct, instrNum);
        curBuffer.curPC = pc;
        pc += 1; // Increment PC
        bufferList.addFirst(curBuffer);
        instructionsFetched++;
      } else {
        writeOutput(" I" + instrNum + "-stall");
      }
    } else if (!stalling) {
      bufferList.addFirst(null);
      endOfInstructionMem = true;
    }
    stalling = false;
    killing = false;
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
    killing = true;
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
    for (int i = PIPELINE_DEPTH - 1; i > 4; i--) {
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
    PipelineBuffer exBuff = getBuffer(4);
    // For every instruction in EX through WB
    for (int i = 5; i < 7; i++) {
      PipelineBuffer forwardBuff = getBuffer(i);
      if (forwardBuff != null) {
        if (exBuff.readReg1 == forwardBuff.writeReg && forwardBuff.opcode == Instruction.Opcode.LD) {
          stall = true;
        } else if (exBuff.readReg2 == forwardBuff.writeReg
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
    writeOutput("REGISTERS", true);
    for (Integer regNum : registerFile.keySet()) {
      writeOutput("R" + regNum + " " + registerFile.get(regNum), true);
    }
    writeOutput("MEMORY", true);
    for (Integer address : mainMemory.keySet()) {
      writeOutput(address + " " + mainMemory.get(address), true);
    }
  }

  public void runSimulation() {
    while (keepGoing()) {
      writeOutput("c#" + cc);
      WB();
      MEM3();
      MEM2();
      MEM1();
      EX();
      ID();
      IF2();
      IF1();
      cc++;
      writeOutput(null, true);
    }
    printResults();
    try {
      outputWriter.flush();
      outputWriter.close();
    } catch (Exception e) {
      System.out.println(e.toString());
    }
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    String inputPath = null;
    System.out.println(new File(".").getAbsolutePath());
    if (args.length > 0) {
      inputPath = args[0];
    } else {
      String doAnother = null;
      do {
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
        
        Simulator sim = new Simulator(inputPath, outputPath);
        sim.runSimulation();
        
        System.out.print("Run another simulation (y/n): ");
        try {
          doAnother = reader.readLine();
        } catch (IOException ioe) {
           System.out.println("IO error reading your choice!");
           System.exit(1);
        }
      } while (doAnother.equals("y"));
      System.out.println("Shutting down.");
    }
    
  }
}
