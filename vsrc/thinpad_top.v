`default_nettype none

module thinpad_top(
    input wire clk_50M,           //50MHz 时钟输入
    input wire clk_11M0592,       //11.0592MHz 时钟输入

    input wire clock_btn,         //BTN5手动时钟按钮开关，带消抖电路，按下时为1
    input wire reset_btn,         //BTN6手动复位按钮开关，带消抖电路，按下时为1

    input  wire[3:0]  touch_btn,  //BTN1~BTN4，按钮开关，按下时为1
    input  wire[31:0] dip_sw,     //32位拨码开关，拨到“ON”时为1
    output wire[15:0] leds,       //16位LED，输出时1点亮
    output wire[7:0]  dpy0,       //数码管低位信号，包括小数点，输出1点亮
    output wire[7:0]  dpy1,       //数码管高位信号，包括小数点，输出1点亮

    //CPLD串口控制器信号
    output wire uart_rdn,         //读串口信号，低有效
    output wire uart_wrn,         //写串口信号，低有效
    input wire uart_dataready,    //串口数据准备好
    input wire uart_tbre,         //发送数据标志
    input wire uart_tsre,         //数据发送完毕标志

    //BaseRAM信号
    inout wire[31:0] base_ram_data,  //BaseRAM数据，低8位与CPLD串口控制器共享
    output wire[19:0] base_ram_addr, //BaseRAM地址
    output wire[3:0] base_ram_be_n,  //BaseRAM字节使能，低有效。如果不使用字节使能，请保持为0
    output wire base_ram_ce_n,       //BaseRAM片选，低有效
    output wire base_ram_oe_n,       //BaseRAM读使能，低有效
    output wire base_ram_we_n,       //BaseRAM写使能，低有效

    //ExtRAM信号
    inout wire[31:0] ext_ram_data,  //ExtRAM数据
    output wire[19:0] ext_ram_addr, //ExtRAM地址
    output wire[3:0] ext_ram_be_n,  //ExtRAM字节使能，低有效。如果不使用字节使能，请保持为0
    output wire ext_ram_ce_n,       //ExtRAM片选，低有效
    output wire ext_ram_oe_n,       //ExtRAM读使能，低有效
    output wire ext_ram_we_n,       //ExtRAM写使能，低有效

    //直连串口信号
    output wire txd,  //直连串口发送端
    input  wire rxd,  //直连串口接收端

    //Flash存储器信号，参考 JS28F640 芯片手册
    output wire [22:0]flash_a,      //Flash地址，a0仅在8bit模式有效，16bit模式无意义
    inout  wire [15:0]flash_d,      //Flash数据
    output wire flash_rp_n,         //Flash复位信号，低有效
    output wire flash_vpen,         //Flash写保护信号，低电平时不能擦除、烧写
    output wire flash_ce_n,         //Flash片选信号，低有效
    output wire flash_oe_n,         //Flash读使能信号，低有效
    output wire flash_we_n,         //Flash写使能信号，低有效
    output wire flash_byte_n,       //Flash 8bit模式选择，低有效。在使用flash的16位模式时请设为1

    //USB 控制器信号，参考 SL811 芯片手册
    output wire sl811_a0,
    inout  wire[7:0] sl811_d,
    output wire sl811_wr_n,
    output wire sl811_rd_n,
    output wire sl811_cs_n,
    output wire sl811_rst_n,
    output wire sl811_dack_n,
    input  wire sl811_intrq,
    input  wire sl811_drq_n,

    //网络控制器信号，参考 DM9000A 芯片手册
    output wire dm9k_cmd,
    inout  wire[15:0] dm9k_sd,
    output wire dm9k_iow_n,
    output wire dm9k_ior_n,
    output wire dm9k_cs_n,
    output wire dm9k_pwrst_n,
    input  wire dm9k_int,

    //图像输出信号
    output wire[2:0] video_red,    //红色像素，3位
    output wire[2:0] video_green,  //绿色像素，3位
    output wire[1:0] video_blue,   //蓝色像素，2位
    output wire video_hsync,       //行同步（水平同步）信号
    output wire video_vsync,       //场同步（垂直同步）信号
    output wire video_clk,         //像素时钟输出
    output wire video_de           //行数据有效信号，用于区分消隐区
);


/* =========== Demo code begin =========== */




//直连串口接收发送演示，从直连串口收到的数据再发送出去
wire [7:0] ext_uart_rx;
wire [7:0] ext_uart_tx;
wire ext_uart_ready, ext_uart_busy;
wire ext_uart_start;

async_receiver #(.ClkFrequency(50000000),.Baud(9600)) //接收模块，9600无检验位
    ext_uart_r(
        .clk(clk_50M),                       //外部时钟信号
        .RxD(rxd),                           //外部串行信号输入
        .RxD_data_ready(ext_uart_ready),  //数据接收到标志
        .RxD_clear(ext_uart_ready),       //清除接收标志
        .RxD_data(ext_uart_rx)             //接收到的一字节数据
    );

async_transmitter #(.ClkFrequency(50000000),.Baud(9600)) //发送模块，9600无检验位
    ext_uart_t(
        .clk(clk_50M),                  //外部时钟信号
        .TxD(txd),                      //串行信号输出
        .TxD_busy(ext_uart_busy),       //发送器忙状态指示
        .TxD_start(ext_uart_start),    //开始发送信号
        .TxD_data(ext_uart_tx)        //待发送的数据
    );

//图像输出演示，分辨率800x600@75Hz，像素时钟为50MHz
wire [11:0] hdata;
assign video_red = hdata < 266 ? 3'b111 : 0; //红色竖条
assign video_green = hdata < 532 && hdata >= 266 ? 3'b111 : 0; //绿色竖条
assign video_blue = hdata >= 532 ? 2'b11 : 0; //蓝色竖条
assign video_clk = clk_50M;
vga #(12, 800, 856, 976, 1040, 600, 637, 643, 666, 1, 1) vga800x600at75 (
    .clk(clk_50M), 
    .hdata(hdata), //横坐标
    .vdata(),      //纵坐标
    .hsync(video_hsync),
    .vsync(video_vsync),
    .data_enable(video_de)
);


wire [31:0] io_ram_dat_i;
wire [31:0] io_ram_dat_o;
wire io_ram_ack_o;
wire [31:0] io_ram_adr_i;
wire io_ram_cyc_i;
wire io_ram_err_o;
wire io_ram_rty_o;
wire [3:0] io_ram_sel_i;
wire io_ram_stb_i;
wire io_ram_we_i;
wire io_ram_stall_o;


wire [31:0] io_ram2_dat_i;
wire [31:0] io_ram2_dat_o;
wire io_ram2_ack_o;
wire [31:0] io_ram2_adr_i;
wire io_ram2_cyc_i;
wire io_ram2_err_o;
wire io_ram2_rty_o;
wire [3:0] io_ram2_sel_i;
wire io_ram2_stb_i;
wire io_ram2_we_i;
wire io_ram2_stall_o;

wire [31:0] io_serial_dat_i;
wire [31:0] io_serial_dat_o;
wire io_serial_ack_o;
wire [31:0] io_serial_adr_i;
wire io_serial_cyc_i;
wire io_serial_err_o;
wire io_serial_rty_o;
wire [3:0] io_serial_sel_i;
wire io_serial_stb_i;
wire io_serial_we_i;
wire io_serial_stall_o;

wire [31:0] io_flash_dat_i;
wire [31:0] io_flash_dat_o;
wire io_flash_ack_o;
wire [31:0] io_flash_adr_i;
wire io_flash_cyc_i;
wire io_flash_err_o;
wire io_flash_rty_o;
wire [3:0] io_flash_sel_i;
wire io_flash_stb_i;
wire io_flash_we_i;
wire io_flash_stall_o;

wire clk_13M;
wire real_clk;

wire io_plic_interface_serial_irq_r;
wire io_plic_interface_serial_permission;
wire io_plic_interface_keyboard_irq_r;
wire io_plic_interface_keyboard_permission;
wire io_plic_interface_net_irq_r;
wire io_plic_interface_net_permission;
wire io_plic_interface_reserved_irq_r;
wire io_plic_interface_reserved_permission;

reg [3:0] cnt;
always @(posedge clk_50M) begin
    if (cnt == 3) begin
        cnt <= 0;
    end
    else begin
        cnt <= cnt + 1;
    end
end

assign clk_13M = (cnt >= 2) ? 1 : 0;

assign real_clk = (dip_sw[0] && dip_sw[1] && dip_sw[30] && dip_sw[31]) ? clk_13M : clock_btn;

SystemOnCat (
    .clock(real_clk),
    .reset(reset_btn),
    .io_devs_touch_btn(touch_btn),
    .io_devs_dip_sw(dip_sw),
    .io_devs_leds(leds),
    .io_devs_dpy0(dpy0),
    .io_devs_dpy1(dpy1),
    .io_ram_dat_i(io_ram_dat_i),
    .io_ram_dat_o(io_ram_dat_o),
    .io_ram_ack_o(io_ram_ack_o),
    .io_ram_adr_i(io_ram_adr_i),
    .io_ram_cyc_i(io_ram_cyc_i),
    .io_ram_err_o(io_ram_err_o),
    .io_ram_rty_o(io_ram_rty_o),
    .io_ram_sel_i(io_ram_sel_i),
    .io_ram_stb_i(io_ram_stb_i),
    .io_ram_we_i(io_ram_we_i),
    .io_ram_stall_o(io_ram_stall_o),
    .io_ram2_dat_i(io_ram2_dat_i),
    .io_ram2_dat_o(io_ram2_dat_o),
    .io_ram2_ack_o(io_ram2_ack_o),
    .io_ram2_adr_i(io_ram2_adr_i),
    .io_ram2_cyc_i(io_ram2_cyc_i),
    .io_ram2_err_o(io_ram2_err_o),
    .io_ram2_rty_o(io_ram2_rty_o),
    .io_ram2_sel_i(io_ram2_sel_i),
    .io_ram2_stb_i(io_ram2_stb_i),
    .io_ram2_we_i(io_ram2_we_i),
    .io_ram2_stall_o(io_ram2_stall_o),
    .io_serial_dat_i(io_serial_dat_i),
    .io_serial_dat_o(io_serial_dat_o),
    .io_serial_ack_o(io_serial_ack_o),
    .io_serial_adr_i(io_serial_adr_i),
    .io_serial_cyc_i(io_serial_cyc_i),
    .io_serial_err_o(io_serial_err_o),
    .io_serial_rty_o(io_serial_rty_o),
    .io_serial_sel_i(io_serial_sel_i),
    .io_serial_stb_i(io_serial_stb_i),
    .io_serial_we_i(io_serial_we_i),
    .io_serial_stall_o(io_serial_stall_o),
    .io_flash_dat_i(io_flash_dat_i),
    .io_flash_dat_o(io_flash_dat_o),
    .io_flash_ack_o(io_flash_ack_o),
    .io_flash_adr_i(io_flash_adr_i),
    .io_flash_cyc_i(io_flash_cyc_i),
    .io_flash_err_o(io_flash_err_o),
    .io_flash_rty_o(io_flash_rty_o),
    .io_flash_sel_i(io_flash_sel_i),
    .io_flash_stb_i(io_flash_stb_i),
    .io_flash_we_i(io_flash_we_i),
    .io_flash_stall_o(io_flash_stall_o),
    .io_plic_interface_serial_irq_r(io_plic_interface_serial_irq_r),
    .io_plic_interface_serial_permission(io_plic_interface_serial_permission),
    .io_plic_interface_keyboard_irq_r(io_plic_interface_keyboard_irq_r),
    .io_plic_interface_keyboard_permission(io_plic_interface_keyboard_irq_r),
    .io_plic_interface_net_irq_r(io_plic_interface_net_irq_r),
    .io_plic_interface_net_permission(io_plic_interface_net_permission),
    .io_plic_interface_reserved_irq_r(io_plic_interface_reserved_irq_r),
    .io_plic_interface_reserved_permission(io_plic_interface_reserved_permission)
);

//module RAMSlave(dat_i, dat_o, ack_o, adr_i, cyc_i,
//    err_o, rty_o, sel_i, stb_i, we_i, stall_o,
//    sram_adr, sram_dat, sram_ce, sram_oe,
//    sram_we, sram_be, clk_bus, rst_bus);

RAMSlave(
    .dat_i(io_ram_dat_i),
    .dat_o(io_ram_dat_o),
    .ack_o(io_ram_ack_o),
    .adr_i(io_ram_adr_i),
    .cyc_i(io_ram_cyc_i),
    .err_o(io_ram_err_o),
    .rty_o(io_ram_rty_o),
    .sel_i(io_ram_sel_i),
    .stb_i(io_ram_stb_i),
    .we_i(io_ram_we_i),
    .stall_o(io_ram_stall_o),
    .sram_adr(base_ram_addr),
    .sram_dat(base_ram_data),
    .sram_ce(base_ram_ce_n),
    .sram_oe(base_ram_oe_n),
    .sram_we(base_ram_we_n),
    .sram_be(base_ram_be_n),
    .clk_bus(real_clk),
    .rst_bus(reset_btn)
);

RAMSlave(
    .dat_i(io_ram2_dat_i),
    .dat_o(io_ram2_dat_o),
    .ack_o(io_ram2_ack_o),
    .adr_i(io_ram2_adr_i),
    .cyc_i(io_ram2_cyc_i),
    .err_o(io_ram2_err_o),
    .rty_o(io_ram2_rty_o),
    .sel_i(io_ram2_sel_i),
    .stb_i(io_ram2_stb_i),
    .we_i(io_ram2_we_i),
    .stall_o(io_ram2_stall_o),
    .sram_adr(ext_ram_addr),
    .sram_dat(ext_ram_data),
    .sram_ce(ext_ram_ce_n),
    .sram_oe(ext_ram_oe_n),
    .sram_we(ext_ram_we_n),
    .sram_be(ext_ram_be_n),
    .clk_bus(real_clk),
    .rst_bus(reset_btn)
);


SerialPortSlave(
    .dat_i(io_serial_dat_i),
    .dat_o(io_serial_dat_o),
    .ack_o(io_serial_ack_o),
    .adr_i(io_serial_adr_i),
    .cyc_i(io_serial_cyc_i),
    .err_o(io_serial_err_o),
    .rty_o(io_serial_rty_o),
    .sel_i(io_serial_sel_i),
    .stb_i(io_serial_stb_i),
    .we_i(io_serial_we_i),
    .stall_o(io_serial_stall_o),
    .uart_clk(clk_50M),
    .uart_busy(ext_uart_busy),
    .uart_ready(ext_uart_ready),
    .uart_start(ext_uart_start),
    .uart_dat_i(ext_uart_rx),
    .uart_dat_o(ext_uart_tx),
    .clk_bus(real_clk),
    .rst_bus(reset_btn),
    .irq(io_plic_interface_serial_irq_r),
    .irq_permitted(io_plic_interface_serial_permission)
);

FlashSlave(
    .dat_i(io_flash_dat_i),
    .dat_o(io_flash_dat_o),
    .ack_o(io_flash_ack_o),
    .adr_i(io_flash_adr_i),
    .cyc_i(io_flash_cyc_i),
    .err_o(io_flash_err_o),
    .rty_o(io_flash_rty_o),
    .sel_i(io_flash_sel_i),
    .stb_i(io_flash_stb_i),
    .we_i(io_flash_we_i),
    .stall_o(io_flash_stall_o),
    .clk_bus(real_clk),
    .rst_bus(reset_btn),
    .flash_clk(clk_11M0592),
    .flash_a(flash_a),
    .flash_d(flash_d),
    .flash_rp_n(flash_rp_n),
    .flash_vpen(flash_vpen),
    .flash_ce_n(flash_ce_n),
    .flash_oe_n(flash_oe_n),
    .flash_we_n(flash_we_n),
    .flash_byte_n(flash_byte_n)
);

assign io_plic_interface_keyboard_irq_r = 0;
assign io_plic_interface_net_irq_r = 0;
assign io_plic_interface_reserved_permission = 0;

endmodule
