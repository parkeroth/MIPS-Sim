public class PipelineBuffer {

  /* Instruction Info */
  public Instruction        instr;
  public int                instructNum;
  public Instruction.Opcode opcode;
  /* Data Items IF */
  public int                curPC;
  /* Data Items ID */
  public int                readReg1;
  public int                readData1;
  public int                readReg2;
  public int                readData2;
  public int                writeReg;
  public int                immediate;
  public int                branchAddr;  // Target address of a branch curPC +
                                         // immediate
  /* Data Items EX */
  public int                aluResult;  // Output of EX ALU
  public boolean            zero;       // Set to 1 if aluResult == 0
  /* Data Items MEM */
  public int                memData;
  /* Control Signals */
  public int                aluSrc;     // Sets source of op2 1=read2
                                         // 0=immediate
  public int                memRead;    // Enables read of main memory
  public int                memWrite;   // Enables write to main memory
  public int                memToReg;   // Sets source of writeData 1=memData
                                         // 0=aluResult
  public int                branch;     // Set if branch is possible
  public int                regWrite;   // Enables write to a register
  public int                regDst;     // Sets destination register 1=rt 0=rd
  /* Other */
  public int                pcSrc;
  public int                nextPC;
  public boolean            getData1;
  public boolean            getData2;

  public PipelineBuffer(Instruction curInstr, int num) {
    instructNum = num;
    instr = curInstr;
    opcode = curInstr.function;
  }

  public boolean branchTaken() {
    if (branch == 0) {
      return false;
    } else {
      switch (opcode) {
      case BNEZ:
        return !zero;
      default:
        return false;
      }
    }
  }

  public void setControlSignals() {
    // Compute Control Signals
    switch (opcode) {
    case DADD:
      regDst = 0;
      regWrite = 1;
      aluSrc = 1;
      memWrite = 0;
      memRead = 0;
      memToReg = 0;
      branch = 0;
      pcSrc = 1;
      break;

    case SUB:
      regDst = 0;
      regWrite = 1;
      aluSrc = 1;
      memWrite = 0;
      memRead = 0;
      memToReg = 0;
      branch = 0;
      pcSrc = 1;
      break;

    case LD:
      regDst = 1;
      regWrite = 1;
      aluSrc = 0;
      memWrite = 0;
      memRead = 1;
      memToReg = 1;
      branch = 0;
      pcSrc = 1;
      break;

    case SD:
      regDst = 0;
      regWrite = 0;
      aluSrc = 0;
      memWrite = 1;
      memRead = 0;
      memToReg = -1;
      branch = 0;
      pcSrc = 1;
      break;

    case BNEZ:
      regDst = -1;
      regWrite = 0;
      aluSrc = 0;
      memWrite = 0;
      memRead = 0;
      memToReg = -1;
      branch = 1;
      pcSrc = 0;
      break;

    default:
      System.out.println("NOT VALID OPCODE");
      break;
    }
  }
}
