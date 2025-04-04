package selector;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import scissors.ScissorsSelectionModel;
import selector.SelectionModel.SelectionState;
import scissors.ScissorsSelectionModel;
/**
 * A graphical application for selecting and extracting regions of images.
 */
public class SelectorApp implements PropertyChangeListener {

    /**
     * Our application window.  Disposed when application exits.
     */
    private final JFrame frame;

    /**
     * Component for displaying the current image and selection tool.
     */
    private final ImagePanel imgPanel;

    /**
     * The current state of the selection tool.  Must always match the model used by `imgPanel`.
     */
    private SelectionModel model;

    /* Components whose state must be changed during the selection process. */
    private JMenuItem saveItem;
    private JMenuItem undoItem;
    private JButton cancelButton;
    private JButton undoButton;
    private JButton resetButton;
    private JButton finishButton;
    private final JLabel statusLabel;

    /**
     * Progress bar to indicate the progress of a model that needs to do long calculations in a
     * "processing" state.
     */
    private JProgressBar processingProgress;

    /**
     * Construct a new application instance.  Initializes GUI components, so must be invoked on the
     * Swing Event Dispatch Thread.  Does not show the application window (call `start()` to do
     * that).
     */
    public SelectorApp() {
        // Initialize application window
        frame = new JFrame("Selector");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Add status bar
        statusLabel = new JLabel();
        frame.add(statusLabel, BorderLayout.PAGE_END);
        statusLabel.setForeground(Color.RED);

        // Add image component with scrollbars
        imgPanel = new ImagePanel();

        JScrollPane scroller = new JScrollPane(imgPanel);
        scroller.setPreferredSize(new Dimension(500,500));
        frame.add(scroller, BorderLayout.CENTER);

        // Add menu bar
        frame.setJMenuBar(makeMenuBar());

        // Add control buttons
        JPanel res = makeControlPanel();
        frame.add(res, BorderLayout.EAST);

        // Add progress bar
        processingProgress = new JProgressBar();
        frame.add(processingProgress, BorderLayout.PAGE_START);

        // Controller: Set initial selection tool and update components to reflect its state
        setSelectionModel(new PointToPointSelectionModel(true));
    }

    /**
     * Create and populate a menu bar with our application's menus and items and attach listeners.
     * Should only be called from constructor, as it initializes menu item fields.
     */
    private JMenuBar makeMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Create and populate File menu
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        JMenuItem openItem = new JMenuItem("Open...");
        fileMenu.add(openItem);
        saveItem = new JMenuItem("Save...");
        fileMenu.add(saveItem);
        JMenuItem closeItem = new JMenuItem("Close");
        fileMenu.add(closeItem);
        JMenuItem exitItem = new JMenuItem("Exit");
        fileMenu.add(exitItem);

        // Create and populate Edit menu
        JMenu editMenu = new JMenu("Edit");
        menuBar.add(editMenu);
        undoItem = new JMenuItem("Undo");
        editMenu.add(undoItem);

        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        closeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, ActionEvent.CTRL_MASK));

        // Controller: Attach menu item listeners
        openItem.addActionListener(e -> openImage());
        closeItem.addActionListener(e -> imgPanel.setImage(null));
        saveItem.addActionListener(e -> saveSelection());
        exitItem.addActionListener(e -> frame.dispose());
        undoItem.addActionListener(e -> model.undo());

        return menuBar;
    }

    /**
     * Return a panel containing buttons for controlling image selection.  Should only be called
     * from constructor, as it initializes button fields.
     */
    private JPanel makeControlPanel() {

        GridLayout verticalLayout = new GridLayout(0, 1);
        JPanel controlPanel = new JPanel(verticalLayout);

        this.cancelButton = new JButton("Cancel");
        this.undoButton = new JButton("Undo");
        this.resetButton = new JButton("Reset");
        this.finishButton = new JButton("Finish");

        controlPanel.add(cancelButton);
        controlPanel.add(undoButton);
        controlPanel.add(resetButton);
        controlPanel.add(finishButton);

        cancelButton.addActionListener(e -> model.cancelProcessing());
        undoButton.addActionListener(e -> model.undo());
        resetButton.addActionListener(e -> model.reset());
        finishButton.addActionListener(e -> model.finishSelection());

        String[] selectModels = {"Point-to-point", "Spline", "Intelligent  Scissors: gray","Intelligent  Scissors: color" };
        JComboBox selectDiffModel = new JComboBox(selectModels);

        selectDiffModel.addActionListener(e -> {

            JComboBox cb = (JComboBox) e.getSource();
            String modelName = (String) cb.getSelectedItem();
            if (modelName.equals(selectModels[0])) {
                setSelectionModel(new PointToPointSelectionModel(model));
            } else if (modelName.equals(selectModels[1])) {
                setSelectionModel(new SplineSelectionModel(model));
            } else if(modelName.equals(selectModels[2])){
                setSelectionModel(new ScissorsSelectionModel("CrossGradMono", model));
            }
            else
            {
                setSelectionModel(new ScissorsSelectionModel("CrossGradColor", model));

            }

        });

        controlPanel.add(selectDiffModel);

        return controlPanel;
    }

    /**
     * Start the application by showing its window.
     */
    public void start() {
        // Compute ideal window size
        frame.pack();

        frame.setVisible(true);
    }

    /**
     * React to property changes in an observed model.  Supported properties include: * "state":
     * Update components to reflect the new selection state.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("state".equals(evt.getPropertyName())) {

            processingProgress.setIndeterminate(model.state().isProcessing());
            reflectSelectionState(model.state());
        }
        else if("progress".equals(evt.getPropertyName()))
        {
            processingProgress.setIndeterminate(false);
            processingProgress.setValue((int)evt.getNewValue());
        }
    }

    /**
     * Update components to reflect a selection state of `state`.  Disable buttons and menu items
     * whose actions are invalid in that state, and update the status bar.
     */
    private void reflectSelectionState(SelectionState state) {
        // Update status bar to show current state
        statusLabel.setText(state.toString());

        if (state.isProcessing()) {
            cancelButton.setEnabled(true);
            resetButton.setEnabled(true);
            undoButton.setEnabled(true);
            finishButton.setEnabled(false);
            saveItem.setEnabled(false);

        } else if (state.canFinish()) {
            cancelButton.setEnabled(false);
            resetButton.setEnabled(true);
            undoButton.setEnabled(true);
            finishButton.setEnabled(true);
            saveItem.setEnabled(false);

        } else if (state.isFinished()) {
            cancelButton.setEnabled(false);
            resetButton.setEnabled(true);
            undoButton.setEnabled(true);
            finishButton.setEnabled(false);
            saveItem.setEnabled(true);


        } else if (state.isEmpty()) {
            cancelButton.setEnabled(false);
            resetButton.setEnabled(false);
            undoButton.setEnabled(false);
            finishButton.setEnabled(false);
            saveItem.setEnabled(false);
        }
    }

    /**
     * Return the model of the selection tool currently in use.
     */
    public SelectionModel getSelectionModel() {
        return model;
    }

    /**
     * Use `newModel` as the selection tool and update our view to reflect its state.  This
     * application will no longer respond to changes made to its previous selection model and will
     * instead respond to property changes from `newModel`.
     */
    public void setSelectionModel(SelectionModel newModel) {
        // Stop listening to old model
        if (model != null) {
            model.removePropertyChangeListener(this);
        }

        imgPanel.setSelectionModel(newModel);
        model = imgPanel.selection();
        model.addPropertyChangeListener("state", this);

        //Listen for "progress" events
        model.addPropertyChangeListener("progress", this);

        // Since the new model's initial state may be different from the old model's state, manually
        //  trigger an update to our state-dependent view.
        reflectSelectionState(model.state());
    }

    /**
     * Start displaying and selecting from `img` instead of any previous image.  Argument may be
     * null, in which case no image is displayed and the current selection is reset.
     */
    public void setImage(BufferedImage img) {
        imgPanel.setImage(img);
    }

    /**
     * Allow the user to choose a new image from an "open" dialog.  If they do, start displaying and
     * selecting from that image.  Show an error message dialog (and retain any previous image) if
     * the chosen image could not be opened.
     */
    private void openImage() {
        JFileChooser chooser = new JFileChooser();
        // Start browsing in current directory
        chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        // Filter for file extensions supported by Java's ImageIO readers
        chooser.setFileFilter(new FileNameExtensionFilter("Image files",
                ImageIO.getReaderFileSuffixes()));

        // Opens the dialog box where a user can look for files.
        // Returns APPROVE_OPTION if the user picked a file successfully.
        boolean successful = false;
        while (!successful) {
            int returnVal = chooser.showOpenDialog(frame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                // Opens the file
                File file = chooser.getSelectedFile();
                System.out.println("Opening: " + file.getName() + ".");
                BufferedImage img = null;
                try {
                    // Attempts to read image data and put it in the img buffer
                    img = ImageIO.read(file);
                    if (img == null) {
                        JOptionPane.showMessageDialog(frame,
                                "The file cannot be read. Ensure the file type is an image.",
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    } else {
                        //Sets img if the image is not null.
                        successful = true;
                        this.setImage(img);

                    }


                } catch (IOException e) {
                    JOptionPane.showMessageDialog(frame, "The image file cannot be read.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            } else if (returnVal == JFileChooser.CANCEL_OPTION) {
                successful = true;

            } else {
                JOptionPane.showMessageDialog(frame, "The image file cannot be read.", "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

    }

    /**
     * Save the selected region of the current image to a file selected from a "save" dialog. Show
     * an error message dialog if the image could not be saved.
     */
    private void saveSelection() {
        JFileChooser chooser = new JFileChooser();
        // Start browsing in current directory
        chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        // We always save in PNG format, so only show existing PNG files
        chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));

        int res = chooser.showSaveDialog(frame);
        if (res == JFileChooser.APPROVE_OPTION) {
            String a = chooser.getSelectedFile().getName();
            if (!a.endsWith(".png") && !a.endsWith(".jpg") && !a.endsWith(".jpeg")) {
                chooser.setSelectedFile(new File(chooser.getSelectedFile().getPath() + ".png"));
            }

            if (chooser.getSelectedFile().exists()) {
                Object[] options = {"Yes", "No"};
                int n = JOptionPane.showOptionDialog(frame,
                        "There is a file with that name. Would you like to overwrite it?",
                        "Overwriting Image",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[1]);

                if (n == JOptionPane.YES_OPTION) {
                    try {
                        FileOutputStream fout = new FileOutputStream(chooser.getSelectedFile());
                        model.saveSelection(fout);
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(frame, "The image file cannot be saved to this directory (access is denied).", "Error",
                                JOptionPane.ERROR_MESSAGE);
                    }

                } else if (n == JOptionPane.NO_OPTION) {
                    saveSelection();
                }

            } else {
                try {
                    FileOutputStream fout = new FileOutputStream(chooser.getSelectedFile());
                    model.saveSelection(fout);
                } catch (IOException ignored) {
                    saveSelection();
                }
            }

        }
    }

    /**
     * Run an instance of SelectorApp.  No program arguments are expected.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Set Swing theme to look the same (and less old) on all operating systems.
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception ignored) {
                /* If the Nimbus theme isn't available, just use the platform default. */
            }

            // Create and start the app
            SelectorApp app = new SelectorApp();
            app.start();
        });
    }
}
