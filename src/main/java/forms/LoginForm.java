package forms;

import controllers.Node;

import javax.swing.*;

public class LoginForm extends JFrame {
	private JTextField usernameField;
	private JPanel panel1;
	private JButton loginButton;

	private Node node = Node.getInstance();

	public LoginForm() {
		setContentPane(panel1);
		loginButton.addActionListener(e -> login());
		usernameField.addActionListener(e -> login());
	}

	private void login() {
		node.login(usernameField.getText());

		JFrame frame = new MainForm();
		frame.pack();
		frame.setResizable(false);
		frame.setVisible(true);

		dispose();
	}
}
