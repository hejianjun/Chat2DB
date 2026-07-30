package ai.chat2db.spi;

import ai.chat2db.spi.model.Command;
import ai.chat2db.spi.model.ExecuteResult;

public interface CommandResultStreamer {

    Iterable<ExecuteResult> stream(Command command, int batchSize);
}
