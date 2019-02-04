package org.frc2851.robot.subsystems;

import badlog.lib.BadLog;
import com.ctre.phoenix.CANifier;
import com.ctre.phoenix.motorcontrol.*;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;
import org.frc2851.crevolib.drivers.TalonSRXFactory;
import org.frc2851.crevolib.io.Axis;
import org.frc2851.crevolib.io.Button;
import org.frc2851.crevolib.io.Controller;
import org.frc2851.crevolib.subsystem.Command;
import org.frc2851.crevolib.subsystem.CommandState;
import org.frc2851.crevolib.subsystem.Subsystem;
import org.frc2851.robot.Constants;
import org.frc2851.robot.Robot;

public class Elevator extends Subsystem
{
    public enum ElevatorControlMode
    {
        DIRECT(ControlMode.PercentOutput, -1),
        MOTION_MAGIC(ControlMode.MotionMagic, 0),
        POS_PID(ControlMode.Position, 1),
        CURRENT(ControlMode.Current, 2);

        private final ControlMode controlMode;
        private final int slotID;
        ElevatorControlMode(ControlMode controlMode, int slotID) {
            this.controlMode = controlMode;
            this.slotID = slotID;
        }

        ControlMode getMode() { return controlMode; }
        int getSlotID() { return slotID; }
    }

    public enum ElevatorPosition
    {
        LOW_HATCH(0), MID_HATCH(0), HIGH_HATCH(0),
        LOW_CARGO(0), MID_CARGO(0), HIGH_CARGO(0);

        private final int pos;
        ElevatorPosition(int pos) {
            this.pos = pos;
        }

        public int getPos() { return pos; }

        @Override
        public String toString() {
            return "[" + this.name() + ", " + pos + "]";
        }
    }

    private Constants mConst = Constants.getInstance();
    private TalonSRX mTalonMaster, mTalonSlave;
    private CANifier mCanifier;
    private Controller mController = (mConst.singleControllerMode) ? Robot.driver : Robot.operator;
    private ElevatorControlMode mControlMode = ElevatorControlMode.DIRECT;
    private ElevatorControlMode mClosedLoopStategy = ElevatorControlMode.MOTION_MAGIC;

    private ElevatorPosition mCurrentPosition = ElevatorPosition.LOW_HATCH;

    private static Elevator mInstance;
    private Elevator() { super("Elevator"); }
    public static Elevator getInstance()
    {
        if (mInstance == null) mInstance = new Elevator();
        return mInstance;
    }

    @Override
    protected boolean init()
    {
        mTalonMaster = TalonSRXFactory.createDefaultMasterTalonSRX(mConst.elevatorMaster);
        mTalonSlave = TalonSRXFactory.createPermanentSlaveTalonSRX(mConst.elevatorSlave, mTalonMaster);
        mCanifier = new CANifier(mConst.elevatorCanifier);

        mTalonMaster.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Relative,0, mConst.talonTimeout);
        mTalonMaster.configReverseLimitSwitchSource(RemoteLimitSwitchSource.RemoteCANifier, LimitSwitchNormal.NormallyOpen,
                mCanifier.getDeviceID(), mConst.talonTimeout);

        TalonSRXFactory.configurePIDF(mTalonMaster, ElevatorControlMode.MOTION_MAGIC.getSlotID(),
                mConst.elevatorMotionP,
                mConst.elevatorMotionI,
                mConst.elevatorMotionD,
                mConst.elevatorMotionF);
        TalonSRXFactory.configurePIDF(mTalonMaster, ElevatorControlMode.POS_PID.getSlotID(),
                mConst.elevatorPosP,
                mConst.elevatorPosI,
                mConst.elevatorPosD,
                mConst.elevatorPosF);

        configureController(mController);

        BadLog.createTopicStr("Elevator/Control Mode", BadLog.UNITLESS, () -> mControlMode.name(), "hide");
        BadLog.createTopic("Elevator/Output Percent", BadLog.UNITLESS, () -> mTalonMaster.getMotorOutputPercent(), "hide");
        BadLog.createTopic("Elevator/Output Voltage Master", "V", () -> mTalonMaster.getBusVoltage(), "hide", "join:Elevator/Voltage Outputs");
        BadLog.createTopic("Elevator/Output Voltage Slave", "V", () -> mTalonSlave.getBusVoltage(), "hide", "join:Elevator/Voltage Outputs");
        BadLog.createTopic("Elevator/Output Current Master", "I", () -> mTalonMaster.getOutputCurrent(), "hide", "join:Elevator/Voltage Outputs");
        BadLog.createTopic("Elevator/Output Current Slave", "I", () -> mTalonSlave.getOutputCurrent(), "hide", "join:Elevator/Voltage Outputs");
        BadLog.createTopic("Elevator/Position", "Counts", () -> (double) mTalonMaster.getSensorCollection().getQuadraturePosition(), "hide");
        return true;
    }

    @Override
    public Command getDefaultCommand() {
        return new Command() {
            ElevatorPosition desiredPosition = null;
            CommandState positionCommandState = getSecondaryCommandState();

            @Override
            public String getName() { return "Teleop"; }

            @Override
            public boolean isFinished() { return false; }

            @Override
            public boolean init() { return true; }

            /*
             *  Desired Operation:
             *      The elevator should always respond to manual input (may need to scale the 
             */
            @Override
            public void update()
            {
                double rawInput = mController.get(Axis.AxisID.RIGHT_Y);
                ElevatorPosition polledPosition = getDesiredPosition();
                desiredPosition = (mCurrentPosition == polledPosition && rawInput == 0) ? null : polledPosition;

                if (desiredPosition != null) {
                    setCommand(setPosition(desiredPosition));
                    // If the command finishes but the init failed, the current position does not change. Otherwise it is set to the desired position
                    if (positionCommandState.isFinished)
                        mCurrentPosition = (positionCommandState.isInit) ? desiredPosition : mCurrentPosition;
                } else {
                    if (!positionCommandState.isFinished) setCommand(null); // Cancels setPosition command. If command is null is finished should be true.
                    mTalonMaster.set(ControlMode.PercentOutput, rawInput);
                }
            }

            @Override
            public void stop() {

            }

            ElevatorPosition getDesiredPosition() {
                if (mController.get(Button.ButtonID.START))
                    return ElevatorPosition.LOW_HATCH;
                else if (mController.get(Button.ButtonID.X))
                    return ElevatorPosition.MID_HATCH;
                else if (mController.get(Button.ButtonID.Y))
                    return ElevatorPosition.HIGH_HATCH;
                else if (mController.get(Button.ButtonID.B))
                    return ElevatorPosition.LOW_CARGO;
                else if (mController.get(Button.ButtonID.A))
                    return ElevatorPosition.MID_CARGO;
                else if (mController.get(Button.ButtonID.SELECT))
                    return ElevatorPosition.HIGH_CARGO;
                return null;
            }
        };
    }

    public Command setPosition(ElevatorPosition pos)
    {
        return new Command() {
            @Override
            public String getName() {
                return "SetPosition(pos: " + pos.name() + ")";
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean init()
            {
                boolean setsSucceeded = true;
                int maxRetrys = 5;
                int count = 0;

                do {
                    mTalonMaster.configMotionCruiseVelocity(mConst.elevatorMaximumVelocity, mConst.talonTimeout);
                    mTalonMaster.configMotionAcceleration(mConst.elevatorMaximumAcceleration, mConst.talonTimeout);
                } while (!setsSucceeded || count++ < maxRetrys);
                mTalonMaster.selectProfileSlot(mClosedLoopStategy.getSlotID(), 0);
                return true;
            }

            @Override
            public void update()
            {
            }

            @Override
            public void stop() {

            }
        };
    }

    public Command moveForTime(double time, double power)
    {
        return new Command() {
            @Override
            public String getName() {
                return "MoveForTime[" + time + "s, " + power + "]";
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean init() {
                return false;
            }

            @Override
            public void update() {

            }

            @Override
            public void stop() {

            }
        };
    }

    public double applyDeadband(double input, double deadband) {
        return (Math.abs(input) < deadband) ? 0 : input;
    }

    public void configureController(Controller controller)
    {
        /*
         * A - Move Elevator to Mid Cargo
         * B - Move Elevator to Low Cargo
         * X - Move Elevator to Mid Hatch
         * Y - Move Elevator to High Hatch
         * Start - Move Elevator to Default Position
         * Select - Move Elevator to High Cargo
         * Right Y - Manual elevator control
         */

        controller.config(Button.ButtonID.A, Button.ButtonMode.RAW);
        controller.config(Button.ButtonID.B, Button.ButtonMode.RAW);
        controller.config(Button.ButtonID.X, Button.ButtonMode.RAW);
        controller.config(Button.ButtonID.Y, Button.ButtonMode.RAW);
        controller.config(Button.ButtonID.START, Button.ButtonMode.RAW);
        controller.config(Button.ButtonID.SELECT, Button.ButtonMode.RAW);
        controller.config(Axis.AxisID.RIGHT_Y, x -> -(applyDeadband(x, 0.15) * mConst.elevatorRawMultiplier));
    }
}
