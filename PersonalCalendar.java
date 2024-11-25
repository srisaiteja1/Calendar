import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;

public class PersonalCalendar {
    private JFrame frame;
    private JTable calendarTable;
    private JLabel monthLabel;
    private LocalDate currentDate;
    private HashMap<String, HashMap<LocalDate, ArrayList<String>>> userEvents;
    private String currentUser;

    private Connection connection;

    public PersonalCalendar() {
        currentDate = LocalDate.now();
        userEvents = new HashMap<>();
        connectToDatabase();
        showLoginDialog();
    }

    private void connectToDatabase() {
        try {
            String url = "jdbc:mysql://localhost:3306/shashi"; 
            String user = "root"; 
            String password = "Shashi@982"; 
            connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database connection failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private void loadEventsFromDatabase() {
        try {
            userEvents.clear();
            String query = "SELECT username, event_date, event_description, event_time FROM user_events";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
    
            while (resultSet.next()) {
                String username = resultSet.getString("username");
                LocalDate date = resultSet.getDate("event_date").toLocalDate();
                String description = resultSet.getString("event_description");
                Time time = resultSet.getTime("event_time");
    
                String eventDetails = time != null ? time.toString() + " - " + description : description;
    
                userEvents.computeIfAbsent(username, k -> new HashMap<>())
                          .computeIfAbsent(date, k -> new ArrayList<>())
                          .add(eventDetails);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Failed to load events: " + e.getMessage());
        }
    }
    
    private void saveEventToDatabase(String username, LocalDate date, String eventDescription, String eventTime) {
        try {
            String query = "INSERT INTO user_events (username, event_date, event_description, event_time) VALUES (?, ?, ?, ?)";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, username);
            preparedStatement.setDate(2, Date.valueOf(date));
            preparedStatement.setString(3, eventDescription);
            preparedStatement.setTime(4, Time.valueOf(eventTime));
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Failed to save event: " + e.getMessage());
        }
    }
    

    private void showLoginDialog() {
        loadEventsFromDatabase();
        String username = JOptionPane.showInputDialog(null, "Enter your username:", "Login", JOptionPane.PLAIN_MESSAGE);
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Username cannot be empty. Exiting.");
            System.exit(0);
        }

        currentUser = username.trim();
        if (!userEvents.containsKey(currentUser)) {
            userEvents.put(currentUser, new HashMap<>());
        }
        setupGUI();
    }

    private void setupGUI() {
        frame = new JFrame("Personal Calendar - User: " + currentUser);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 500);

        JPanel topPanel = new JPanel(new BorderLayout());
        JButton prevButton = new JButton("<");
        JButton nextButton = new JButton(">");
        monthLabel = new JLabel("", SwingConstants.CENTER);
        JButton logoutButton = new JButton("Logout");

        prevButton.addActionListener(e -> changeMonth(-1));
        nextButton.addActionListener(e -> changeMonth(1));
        logoutButton.addActionListener(e -> logout());

        topPanel.add(prevButton, BorderLayout.WEST);
        topPanel.add(monthLabel, BorderLayout.CENTER);
        topPanel.add(nextButton, BorderLayout.EAST);

        JPanel logoutPanel = new JPanel();
        logoutPanel.add(logoutButton);
        topPanel.add(logoutPanel, BorderLayout.SOUTH);

        calendarTable = new JTable();
        calendarTable.setCellSelectionEnabled(true);
        calendarTable.setRowHeight(80);

        calendarTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        
                if (value != null) {
                    int day = (int) value;
                    LocalDate currentCellDate = LocalDate.of(currentDate.getYear(), currentDate.getMonth(), day);
                    ArrayList<String> eventsForDay = userEvents.get(currentUser).getOrDefault(currentCellDate, new ArrayList<>());
        
                    StringBuilder cellText = new StringBuilder("<html><div style='text-align: center;'>");
                    cellText.append(day);
        
                    if (!eventsForDay.isEmpty()) {
                        cellText.append("<br><font color='blue' size='2'>");
                        for (String event : eventsForDay) {
                            cellText.append("• ").append(event).append("<br>");
                        }
                        cellText.append("</font>");
                        setBackground(new Color(144, 238, 144)); // Light green for days with events
                    } else {
                        setBackground(isSelected ? new Color(173, 216, 230) : Color.white); // Highlight selected cell
                    }
        
                    cellText.append("</div></html>");
                    setText(cellText.toString());
                } else {
                    setText("");
                    setBackground(Color.white);
                }
        
                return cell;
            }
        });
        
        

        calendarTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = calendarTable.getSelectedRow();
                int col = calendarTable.getSelectedColumn();
                if (row >= 0 && col >= 0) {
                    Object dayObj = calendarTable.getValueAt(row, col);
                    if (dayObj != null) {
                        int day = (int) dayObj;
                        LocalDate selectedDate = LocalDate.of(currentDate.getYear(), currentDate.getMonthValue(), day);
                        showEventDialog(selectedDate);
                    }
                }
            }
        });

        updateCalendar();

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(new JScrollPane(calendarTable), BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private void updateCalendar() {
        monthLabel.setText(currentDate.getMonth() + " " + currentDate.getYear());
        DefaultTableModel model = new DefaultTableModel();
        model.setColumnIdentifiers(new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"});

        LocalDate firstDayOfMonth = currentDate.withDayOfMonth(1);
        int startDayOfWeek = firstDayOfMonth.getDayOfWeek().getValue() % 7;
        int daysInMonth = currentDate.lengthOfMonth();

        Object[][] data = new Object[6][7];
        int day = 1;

        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                if (i == 0 && j < startDayOfWeek) {
                    data[i][j] = null;
                } else if (day <= daysInMonth) {
                    data[i][j] = day++;
                } else {
                    data[i][j] = null;
                }
            }
        }

        model.setDataVector(data, new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"});
        calendarTable.setModel(model);
    }

    private void changeMonth(int delta) {
        currentDate = currentDate.plusMonths(delta);
        updateCalendar();
    }

    private void showEventDialog(LocalDate date) {
        ArrayList<String> eventsForDate = userEvents.get(currentUser).getOrDefault(date, new ArrayList<>());
        StringBuilder eventsForDateText = new StringBuilder("Events for " + date + ":\n");
    
        if (!eventsForDate.isEmpty()) {
            for (String event : eventsForDate) {
                eventsForDateText.append("- ").append(event).append("\n");
            }
        } else {
            eventsForDateText.append("No events.");
        }
    
        JPanel panel = new JPanel(new GridLayout(2, 2));
        panel.add(new JLabel("New Event:"));
        JTextField eventField = new JTextField();
        panel.add(eventField);
        panel.add(new JLabel("Event Time (HH:MM:SS):"));
        JTextField timeField = new JTextField();
        panel.add(timeField);
    
        int result = JOptionPane.showConfirmDialog(frame, panel, eventsForDateText.toString(), JOptionPane.OK_CANCEL_OPTION);
    
        if (result == JOptionPane.OK_OPTION) {
            String newEvent = eventField.getText().trim();
            String eventTime = timeField.getText().trim();
    
            if (!newEvent.isEmpty() && !eventTime.isEmpty()) {
                userEvents.get(currentUser).computeIfAbsent(date, k -> new ArrayList<>())
                          .add(eventTime + " - " + newEvent);
                saveEventToDatabase(currentUser, date, newEvent, eventTime);
                JOptionPane.showMessageDialog(frame, "Event added!");
                updateCalendar();
            }
        }
    }
    

    private void logout() {
        int option = JOptionPane.showConfirmDialog(frame, "Are you sure you want to log out?", "Logout", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            frame.dispose();
            showLoginDialog();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PersonalCalendar::new);
    }
}
