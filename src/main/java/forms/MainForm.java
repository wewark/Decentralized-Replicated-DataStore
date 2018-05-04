package forms;

import controllers.Node;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MainForm extends JFrame {
	private JList userList;
	private JPanel panel1;
	private JButton syncButton;
	private JButton commitChangesButton;

	private Node node = Node.getInstance();

	DefaultListModel listModel = new DefaultListModel();

	MainForm() {
		setContentPane(panel1);
		userList.setModel(listModel);

		node.setUpdateUserList(() -> {
			updateUserList();
			return null;
		});

		syncButton.addActionListener(e -> node.scanDirectory());

		commitChangesButton.addActionListener(e -> node.commitChanges());
	}

	private void updateUserList() {
		System.out.println("Updating user list...");
		String[] users = node.getUsernames();
		listModel.clear();
		for (String username : users)
			listModel.addElement(username);
	}
}
