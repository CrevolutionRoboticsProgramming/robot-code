package org.frc2851.robot.subsystems;

import badlog.lib.BadLog;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;
import edu.wpi.first.wpilibj.DigitalInput;
import org.frc2851.crevolib.Logger;
import org.frc2851.crevolib.drivers.TalonCommunicationErrorException;
import org.frc2851.crevolib.drivers.TalonSRXFactory;
import org.frc2851.crevolib.io.Button;
import org.frc2851.crevolib.io.Controller;
import org.frc2851.crevolib.subsystem.Command;
import org.frc2851.crevolib.subsystem.Subsystem;
import org.frc2851.robot.Constants;
import org.frc2851.robot.Robot;
// button id references for ME to use.(you can use them too if you want)
/*A(1), B(2), X(3), Y(4), START(8), SELECT(7), LEFT_BUMPER(5), RIGHT_BUMPER(6),
        LEFT_JOYSTICK(9), RIGHT_JOYSTICK(10);
        LEFT_X(0), LEFT_Y(1), RIGHT_X(4), RIGHT_Y(5), LEFT_TRIGGER(2), RIGHT_TRIGGER(3);*/

/**
 * Represents the climber subsystem
 */
public class Climber extends Subsystem
{
    public enum ClimberState
    {
        EXTENDING(1.0), RETRACTING(-1.0), NEUTRAL(0.0);

        private double output;

        ClimberState(double output)
        {
            this.output = output;
        }

        public double getOutput()
        {
            return output;
        }
    }

    private Constants mConstants = Constants.getInstance();
    private Controller mController = Robot.driver;
    private static Climber mInstance = new Climber();

    private DigitalInput gorillaLimitOut, gorillaLimitIn, screwLimitOut, screwLimitIn;

    private ClimberState gorillaState = ClimberState.NEUTRAL, lastGorillaState = ClimberState.NEUTRAL;
    private ClimberState screwState = ClimberState.NEUTRAL, lastScrewState = ClimberState.NEUTRAL;

    /**
     * Returns the sole instance of the Climber class
     *
     * @return The instance of the Climber class
     */
    public static Climber getInstance()
    {
        return mInstance;
    }

    /**
     * Initializes the Climber class with the name "Climber"
     */
    private Climber()
    {
        super("Climber");
    }

    private WPI_TalonSRX _gorillaMaster, _gorillaSlave, _screwMaster;

    /**
     * Initializes the controller, motors, and logging
     *
     * @return A boolean representing whether initialization has succeeded
     */
    @Override
    public boolean init()
    {
        mController.config(mConstants.climberGorillaButton, Button.ButtonMode.TOGGLE);
        mController.config(mConstants.climberScrewButton, Button.ButtonMode.TOGGLE);

        try
        {
            _gorillaMaster = TalonSRXFactory.createDefaultWPI_TalonSRX(mConstants.gorillaMaster);
            _gorillaSlave = TalonSRXFactory.createPermanentSlaveWPI_TalonSRX(mConstants.gorillaSlave, _gorillaMaster);
            _screwMaster = TalonSRXFactory.createDefaultWPI_TalonSRX(mConstants.screwMaster);
        } catch (TalonCommunicationErrorException e)
        {
            log("Could not initialize motor, climber init failed! Port: " + e.getPortNumber(), Logger.LogLevel.ERROR);
            return false;
        }

        gorillaLimitOut = new DigitalInput(mConstants.gorillaLimitOut);
        gorillaLimitIn = new DigitalInput(mConstants.gorillaLimitIn);
        screwLimitOut = new DigitalInput(mConstants.screwLimitOut);
        screwLimitIn = new DigitalInput(mConstants.screwLimitIn);

        BadLog.createTopic("Climber/Master", BadLog.UNITLESS, () -> _gorillaMaster.getMotorOutputPercent(), "hide", "join:Climber/Percent Output");
        BadLog.createTopic("Climber/Slave", BadLog.UNITLESS, () -> _gorillaSlave.getMotorOutputPercent(), "hide", "join:Climber/Percent Output");
        BadLog.createTopic("Climber/Screw", BadLog.UNITLESS, () -> _screwMaster.getMotorOutputPercent(), "hide", "join:Climber/Percent Output");
        BadLog.createTopic("Climber/Master", "Voltage:", () -> _gorillaMaster.getBusVoltage(), "hide", "join:Climber/Voltage Outputs");
        BadLog.createTopic("Climber/Slave", "Voltage:", () -> _gorillaSlave.getBusVoltage(), "hide", "join:Climber/Voltage Outputs");
        BadLog.createTopic("Climber/Screw", "Voltage:", () -> _screwMaster.getBusVoltage(), "hide", "join:Climber/Percent Output");
        BadLog.createTopic("Climber/Master", "Amperes:", () -> _gorillaMaster.getOutputCurrent(), "hide", "join:Climber/Current Outputs");
        BadLog.createTopic("Climber/Slave", "Amperes:", () -> _gorillaSlave.getOutputCurrent(), "hide", "join:Climber/Current Outputs");
        BadLog.createTopic("Climber/Screw", "Amperes:", () -> _screwMaster.getOutputCurrent(), "hide", "join:Climber/Percent Output");

        return true;
    }

    // Makes the selected talon go to the x value
    /*
    0=gorilla arm
    1=actuating screw*/
/*private void TalonSet(double x,int select){
    if (select==0) {
        _gorillaMaster.set(ControlMode.PercentOutput, x);
    }else if(select==1){
        _screwMaster.set(ControlMode.PercentOutput,x);
    }
}*/

    /**
     * Resets the motors
     */
    private void reset()
    {
        _gorillaMaster.set(ControlMode.PercentOutput, 0);
        _screwMaster.set(ControlMode.PercentOutput, 0);
        log("All motors zeroed", Logger.LogLevel.DEBUG);
    }

    /**
     * Returns a command representing user control over the climber
     *
     * @return A command representing user control over the climber
     */
    @Override
    public Command getDefaultCommand()
    {
        return new Command()
        {
            @Override
            public String getName()
            {
                return "Teleop";
            }

            @Override
            public boolean isFinished()
            {
                return false;
            }

            @Override
            public boolean init()
            {
                reset();
                return true;
            }

            @Override
            public void update()
            {
                // Gorilla arm
                if (mController.get(mConstants.climberGorillaButton))
                {
                    if (!gorillaLimitOut.get())
                    {
                        gorillaState = ClimberState.EXTENDING;
                    }
                } else if (!gorillaLimitIn.get())
                {
                    gorillaState = ClimberState.RETRACTING;
                }

                // Screw drive
                if (mController.get(mConstants.climberScrewButton))
                {
                    if (!screwLimitOut.get())
                    {
                        screwState = ClimberState.EXTENDING;
                    }
                } else if (!screwLimitIn.get())
                {
                    screwState = ClimberState.RETRACTING;
                }

                _gorillaMaster.set(ControlMode.PercentOutput, gorillaState.getOutput());
                _screwMaster.set(ControlMode.PercentOutput, screwState.getOutput());

                if (lastGorillaState != gorillaState)
                {
                    log("Gorilla arm set to " + gorillaState.toString(), Logger.LogLevel.DEBUG);
                }
                if (lastScrewState != screwState)
                {
                    log("Screw set to " + screwState.toString(), Logger.LogLevel.DEBUG);
                }

                lastGorillaState = gorillaState;
                lastScrewState = screwState;
            }

            @Override
            public void stop()
            {
                reset();
            }
        };
    }
}