import javax.swing.*;

public class LoginForm {
	private JTextField usernameField;
	public JPanel panel1;
	private JButton loginButton;

	private Node node = Node.getInstance();

	LoginForm() {
		loginButton.addActionListener(e -> {
			node.login(usernameField.getText());
		});
		usernameField.addActionListener(e -> {
			node.login(usernameField.getText());
		});
	}
}
