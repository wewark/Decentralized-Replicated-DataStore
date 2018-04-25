import net.engio.mbassy.listener.Handler;
import net.engio.mbassy.listener.Listener;
import net.engio.mbassy.listener.References;
import org.apache.commons.io.FileUtils;
import org.hive2hive.core.api.interfaces.IFileManager;
import org.hive2hive.core.events.framework.interfaces.IFileEventListener;
import org.hive2hive.core.events.framework.interfaces.file.*;

import java.io.IOException;

// A Strong reference is necessary if this object is not held in any variable, otherwise GC would clean it
// and events are not triggered anymore. So keep either a reference to this listener object or add the
// strong reference annotation.
@Listener(references = References.Strong)
class IFileEventListenerImpl implements IFileEventListener {

	private final IFileManager fileManager;

	public IFileEventListenerImpl(IFileManager fileManager) {
		this.fileManager = fileManager;
	}

	@Handler
	public void onFileAdd(IFileAddEvent fileEvent) {
		System.out.println("File was added: " + fileEvent.getFile().getName());
		try {
			// download the new file
			fileManager.createDownloadProcess(fileEvent.getFile()).execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Handler
	public void onFileUpdate(IFileUpdateEvent fileEvent) {
		System.out.println("File was updated: " + fileEvent.getFile().getName());
		try {
			// download the newest version
			fileManager.createDownloadProcess(fileEvent.getFile()).execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Handler
	public void onFileDelete(IFileDeleteEvent fileEvent) {
		System.out.println("File was deleted: " + fileEvent.getFile().getName());
		// delete it at the event receiver as well
		fileEvent.getFile().delete();
	}

	@Handler
	public void onFileMove(IFileMoveEvent fileEvent) {
		try {
			// Move the file to the new destination if it exists
			if (fileEvent.isFile() && fileEvent.getSrcFile().exists()) {
				FileUtils.moveFile(fileEvent.getSrcFile(), fileEvent.getDstFile());
				System.out.println("File was moved from " + fileEvent.getSrcFile() + " to " + fileEvent.getDstFile());
			} else if (fileEvent.isFolder() && fileEvent.getSrcFile().exists()) {
				FileUtils.moveDirectory(fileEvent.getSrcFile(), fileEvent.getDstFile());
				System.out.println("Folder was moved from " + fileEvent.getSrcFile() + " to " + fileEvent.getDstFile());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Handler
	public void onFileShare(IFileShareEvent fileEvent) {
		System.out.println("File was shared by " + fileEvent.getInvitedBy());
		// Currently, no further actions necessary. The invitation is accepted
		// automatically and 'onFileAdd' is called in an instant.
	}

}