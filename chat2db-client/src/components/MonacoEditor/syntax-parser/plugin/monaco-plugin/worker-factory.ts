// Worker factory to avoid webpack import issues
export const createParserWorker = () => {
  try {
    // 使用 webpack 的 worker-loader 方式
    const worker = new Worker(
      new URL('./parser.worker.js', import.meta.url)
    );
    return worker;
  } catch (error) {
    console.warn('[Worker] Failed to create worker:', error);
    return null;
  }
};
