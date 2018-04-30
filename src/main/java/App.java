import forms.LoginForm;

import javax.swing.*;

public class App {
	public static void main(String[] args) {
		JFrame frame = new LoginForm();
		frame.pack();
		frame.setResizable(false);
		frame.setVisible(true);
	}
}
