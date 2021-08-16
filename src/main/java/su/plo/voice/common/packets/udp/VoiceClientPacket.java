package su.plo.voice.common.packets.udp;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import lombok.AllArgsConstructor;
import lombok.Getter;
import su.plo.voice.common.packets.Packet;

import java.io.IOException;

@AllArgsConstructor
public class VoiceClientPacket implements Packet {
    @Getter
    private byte[] data;
    @Getter
    private long sequenceNumber;
    @Getter
    private short distance;

    public VoiceClientPacket() {}

    @Override
    public void read(ByteArrayDataInput buf) throws IOException {
        int length = buf.readInt();
        byte[] data = new byte[length];
        buf.readFully(data);
        this.data = data;
        this.distance = buf.readShort();
        this.sequenceNumber = buf.readLong();
    }

    @Override
    public void write(ByteArrayDataOutput buf) throws IOException {
        buf.writeInt(data.length);
        buf.write(data);
        buf.writeShort(distance);
        buf.writeLong(sequenceNumber);
    }
}