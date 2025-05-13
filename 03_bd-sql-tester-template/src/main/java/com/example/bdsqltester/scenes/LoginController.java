package com.example.bdsqltester.scenes;

import com.example.bdsqltester.HelloApplication;
import com.example.bdsqltester.datasources.MainDataSource;
import com.example.bdsqltester.scenes.user.UserController;
import com.example.bdsqltester.dtos.User;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.sql.*;

public class LoginController {

    @FXML
    private PasswordField passwordField;

    @FXML
    private ChoiceBox<String> selectRole;

    @FXML
    private TextField usernameField;

    private User getUserByUsername(String username) throws SQLException {
        try (Connection c = MainDataSource.getConnection()) {
            PreparedStatement stmt = c.prepareStatement("SELECT id, username, password, role FROM users WHERE username = ?");
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                User user = new User(rs.getString("username"), rs.getString("password"), rs.getString("role"));
                user.setId(rs.getLong("id"));
                return user;
            }
            return null;
        }
    }

    boolean verifyCredentials(String username, String password, String role) throws SQLException {
        try (Connection c = MainDataSource.getConnection()) {
            PreparedStatement stmt = c.prepareStatement("SELECT password FROM users WHERE username = ? AND role = ?");
            stmt.setString(1, username);
            stmt.setString(2, role.toLowerCase());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String dbPassword = rs.getString("password");
                return dbPassword.equals(password);
            }
            return false;
        }
    }

    @FXML
    void initialize() {
        selectRole.getItems().addAll("Admin", "User");
        selectRole.setValue("User");
    }

    @FXML
    void onLoginClick(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String role = selectRole.getValue();

        try {
            if (verifyCredentials(username, password, role)) {
                HelloApplication app = HelloApplication.getApplicationInstance();
                FXMLLoader loader;
                Scene scene;

                if (role.equals("Admin")) {
                    app.getPrimaryStage().setTitle("Admin View");
                    loader = new FXMLLoader(HelloApplication.class.getResource("admin-view.fxml"));
                    scene = new Scene(loader.load());
                    app.getPrimaryStage().setScene(scene);
                } else {
                    app.getPrimaryStage().setTitle("User View");
                    loader = new FXMLLoader(HelloApplication.class.getResource("user-view.fxml"));
                    scene = new Scene(loader.load());
                    app.getPrimaryStage().setScene(scene);
                    UserController userController = loader.getController();
                    try {
                        User loggedInUser = getUserByUsername(username);
                        if (loggedInUser != null) {
                            System.out.println("Logged in user ID in LoginController: " + loggedInUser.getId());
                            userController.setLoggedInUserId(loggedInUser.getId());
                        } else {
                            showAlert("Error", "Gagal","Gagal mendapatkan informasi pengguna setelah login");
                        }
                    } catch (SQLException e) {
                        showAlert("Database Error", "Gagal mendapatkan informasi pengguna.", e.getMessage());
                    }
                }
            } else {
                showAlert("Login Failed", "Invalid Credentials", "Please check your username and password.");
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Database Connection Failed", "Could not connect to the database. Please try again later.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void showAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
