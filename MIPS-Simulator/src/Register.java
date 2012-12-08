public class Register {
    private int reg_num;
    private int reg_val;
    private boolean ready;

    Register(int num, int val){
        reg_num = num;
        reg_val = val;
    }

    int getValue(){
        return reg_val;
    }

    void setValue(int val) {
        reg_val = val;
    }

    void setReady(boolean val) {
        ready = val;
    }

    boolean isReady(){
        return ready;
    }
}
