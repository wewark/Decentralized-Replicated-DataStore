import forms.LoginForm;
import storage.FileManager;

import javax.swing.*;

public class App {
	public static void main(String[] args) {
		FileManager.setRootDir();
		JFrame frame = new LoginForm();
		frame.pack();
		frame.setResizable(false);
		frame.setVisible(true);
		frame.setLocationRelativeTo(null);
	}
}
