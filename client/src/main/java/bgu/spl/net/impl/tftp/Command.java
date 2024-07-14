package bgu.spl.net.impl.tftp;

public enum Command {
    RRQ(1),
    WRQ(2),
    DATA(3),
    ACK(4),
    ERROR(5),
    DIRQ(6),
    LOGRQ(7),
    DELRQ(8),
    BCAST(9),
    DISC(10);

    private final int value;

    Command(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }
}
