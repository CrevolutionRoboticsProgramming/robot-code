package org.frc2851.robot.subsystems;
//when b is pressed, the screw drive actuates(moves)

import badlog.lib.BadLog;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;
import org.frc2851.crevolib.Logger;
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

public class Climber extends Subsystem {

    private Constants mConstants = Constants.getInstance();
    private Controller mController = Robot.driver;

    private static Climber mInstance = new Climber();

    public static Climber getInstance() {
        return mInstance;
    }
    private Climber() {
        super("Climber");
    }

    private WPI_TalonSRX _gorillaMaster, _gorillaSlave, _screwMaster;

    @Override
    public boolean init() {
        mController.config(Button.ButtonID.A, Button.ButtonMode.TOGGLE);
        mController.config(Button.ButtonID.B, Button.ButtonMode.TOGGLE);

        _gorillaMaster = TalonSRXFactory.createDefaultMasterWPI_TalonSRX(mConstants.gorillaMaster);
        _gorillaSlave = TalonSRXFactory.createPermanentSlaveWPI_TalonSRX(mConstants.gorillaSlave, _gorillaMaster);
        _screwMaster = TalonSRXFactory.createDefaultMasterWPI_TalonSRX(mConstants.screwMaster);
        Logger.println("Init worked", Logger.LogLevel.DEBUG);
        BadLog.createTopic("Climber/Master", BadLog.UNITLESS, () -> _gorillaMaster.getMotorOutputPercent(), "hide", "join:Climber/Percent Output");
        BadLog.createTopic("Climber/Slave", BadLog.UNITLESS, () -> _gorillaSlave.getMotorOutputPercent(), "hide", "join:Climber/Percent Output");
        BadLog.createTopic("Climber/Screw", BadLog.UNITLESS, () -> _screwMaster.getMotorOutputPercent(), "hide", "join:Climber/Percent Output");
        BadLog.createTopic("Climber/Master", "Voltage:", () -> _gorillaMaster.getBusVoltage(), "hide", "join:Climber/Voltage Outputs");
        BadLog.createTopic("Climber/Slave", "Voltage:", () -> _gorillaSlave.getBusVoltage(), "hide", "join:Climber/Voltage Outputs");
        BadLog.createTopic("Climber/Screw","Voltage:", () -> _screwMaster.getBusVoltage(), "hide", "join:Climber/Percent Output");
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

    private void reset() {
        _gorillaMaster.set(ControlMode.PercentOutput, 0);
        _screwMaster.set(ControlMode.PercentOutput, 0);
        Logger.println("All climb motors zeroed", Logger.LogLevel.DEBUG);
    }

    @Override
    public Command getTeleopCommand() {
        return new Command() {
            @Override
            public String getName() {
                return "Teleop";
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean init() {
                reset();
                return true;
            }

            @Override
            public void update() {
                // Gorilla arm
                if (mController.get(Button.ButtonID.A)) {
                    _gorillaMaster.set(ControlMode.PercentOutput, 1.0);
                    Logger.println("Raised Gorilla", Logger.LogLevel.DEBUG);
                } else {
                    _gorillaMaster.set(ControlMode.PercentOutput, -1.0);
                    Logger.println("Lowered Gorilla", Logger.LogLevel.DEBUG);
                }

                // Screw drive
                if (mController.get(Button.ButtonID.B)) {
                    _screwMaster.set(ControlMode.PercentOutput, 1.0);
                    Logger.println("Enabled Screw", Logger.LogLevel.DEBUG);
                } else {
                    _screwMaster.set(ControlMode.PercentOutput, -1.0);
                    Logger.println("Disabled Screw", Logger.LogLevel.DEBUG);
                }
            }

            @Override
            public void stop() {
                reset();
            }
        };
    }
}